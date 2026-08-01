package com.nuguyo.app.data.sheet

import android.util.Log
import com.nuguyo.app.data.EmployeeRepository
import com.nuguyo.app.data.SettingsStore
import java.io.IOException

sealed interface SyncResult {
    data class Success(
        val added: Int,
        val updated: Int,
        val removed: Int,
        val issues: List<SheetRowIssue>,
    ) : SyncResult {
        val touched: Int get() = added + updated
    }

    data class Failure(val reason: String) : SyncResult
}

/**
 * 구글 시트를 원본으로 삼아 직원 명부를 맞춘다. 단방향(시트 → 앱)이다.
 *
 * 앱에서 직접 만든 직원([com.nuguyo.app.domain.model.EmployeeSource.LOCAL])은
 * 절대 건드리지 않는다. 시트를 붙였다는 이유로 사용자가 손으로 넣은 명부가
 * 사라지면 안 되기 때문이다.
 */
class SheetSync(
    private val employees: EmployeeRepository,
    private val settings: SettingsStore,
    private val client: SheetDirectoryClient = SheetDirectoryClient(),
) {

    suspend fun sync(): SyncResult {
        val configured = settings.sheetUrl
        if (configured.isNullOrBlank()) {
            return SyncResult.Failure("연동된 시트가 없습니다")
        }
        val csvUrl = GoogleSheetUrl.toCsvUrl(configured)
            ?: return SyncResult.Failure("구글 시트 주소가 아닙니다")

        val parsed = try {
            val csv = client.fetchCsv(csvUrl)
            SheetRowMapper.map(CsvParser.parse(csv))
        } catch (e: SheetFetchException) {
            return fail(e.message ?: "시트를 불러오지 못했습니다")
        } catch (e: IOException) {
            // 통신 실패로 기존 명부를 날리면 안 된다. 그대로 두고 물러난다.
            Log.w(TAG, "시트를 불러오지 못했다", e)
            return fail("네트워크에 연결할 수 없습니다")
        }

        if (parsed.employees.isEmpty()) {
            val reason = parsed.issues.firstOrNull()?.reason ?: "시트에 직원이 없습니다"
            return fail(reason)
        }

        return try {
            apply(parsed).also { record(it) }
        } catch (e: Exception) {
            Log.w(TAG, "시트 내용을 저장하지 못했다", e)
            fail("명부를 저장하지 못했습니다")
        }
    }

    private suspend fun apply(parsed: SheetParseResult): SyncResult.Success {
        val existing = employees.findFromSheet().associateBy { it.sourceKey }

        var added = 0
        var updated = 0
        parsed.employees.forEach { incoming ->
            val previous = existing[incoming.sourceKey]
            // 기존 행이면 id 를 물려줘야 수신 이력이 그 사람에게 계속 붙는다.
            employees.save(incoming.copy(id = previous?.id.orEmpty()))
            if (previous == null) added++ else updated++
        }

        val incomingKeys = parsed.employees.mapNotNull { it.sourceKey }.toSet()
        val removedEmployees = existing.values.filter { it.sourceKey !in incomingKeys }
        removedEmployees.forEach { employees.delete(it.id) }

        return SyncResult.Success(
            added = added,
            updated = updated,
            removed = removedEmployees.size,
            issues = parsed.issues,
        )
    }

    private fun record(result: SyncResult.Success) {
        val summary = buildString {
            append("직원 ${result.touched}명")
            if (result.removed > 0) append(" · 삭제 ${result.removed}명")
            if (result.issues.isNotEmpty()) append(" · 건너뜀 ${result.issues.size}줄")
        }
        settings.recordSync(success = true, summary = summary)
    }

    private fun fail(reason: String): SyncResult.Failure {
        settings.recordSync(success = false, summary = reason)
        return SyncResult.Failure(reason)
    }

    private companion object {
        const val TAG = "SheetSync"
    }
}
