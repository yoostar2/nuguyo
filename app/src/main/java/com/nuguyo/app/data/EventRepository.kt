package com.nuguyo.app.data

import com.nuguyo.app.data.db.ContactEventDao
import com.nuguyo.app.data.db.ContactEventEntity
import com.nuguyo.app.domain.model.ContactEvent
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.domain.model.ContactSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import java.util.concurrent.TimeUnit

class EventRepository(private val dao: ContactEventDao) {

    fun observeRecent(limit: Int = 300): Flow<List<HistoryRow>> =
        dao.observeRecent(limit).map { rows ->
            rows.map { row ->
                HistoryRow(
                    event = ContactEvent(
                        id = row.event.id,
                        employeeId = row.event.employeeId,
                        number = row.event.number,
                        kind = row.event.kind.toKind(),
                        at = row.event.at,
                        preview = row.event.preview,
                    ),
                    employeeName = row.employeeName,
                )
            }
        }

    suspend fun record(
        employeeId: String?,
        number: String,
        kind: ContactKind,
        preview: String? = null,
        at: Long = System.currentTimeMillis(),
    ) {
        dao.insert(
            ContactEventEntity(
                id = UUID.randomUUID().toString(),
                employeeId = employeeId,
                number = number,
                kind = kind.name,
                at = at,
                preview = preview,
            ),
        )
    }

    /** 팝업에 뿌릴 "최근 N회 · 마지막 연락" 요약. [before] 이번 이벤트 자신은 제외. */
    suspend fun summaryFor(employeeId: String, before: Long): ContactSummary =
        ContactSummary(
            totalCount = dao.countFor(employeeId),
            lastContactAt = dao.lastContactBefore(employeeId, before),
        )

    suspend fun pruneOlderThan(days: Long = 180) {
        dao.deleteOlderThan(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days))
    }

    suspend fun clear() = dao.clear()
}

data class HistoryRow(
    val event: ContactEvent,
    val employeeName: String?,
)

private fun String.toKind(): ContactKind =
    runCatching { ContactKind.valueOf(this) }.getOrDefault(ContactKind.CALL)
