package com.nuguyo.app.domain.lookup

import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer

/** 번호로 직원을 찾는 데 필요한 최소 조회면. 테스트에서 갈아끼울 수 있게 분리했다. */
interface CallerDirectory {
    suspend fun findByE164(e164: String): List<Employee>
    suspend fun findByLoose(loose: String): List<Employee>

    /** DB 를 미리 열어 두기 위한 가벼운 조회. */
    suspend fun touch()
}

/**
 * 걸려온 번호로 직원을 찾아낸다.
 *
 * 색인된 컬럼(`e164`, `loose`)만 조회하므로 SQLite 왕복 두 번이면 끝난다.
 * 별도의 메모리 캐시를 두지 않은 건 의도적이다 — 명부 편집과 캐시 무효화가
 * 어긋나 "수정했는데 옛 이름이 뜨는" 버그가 나는 쪽이 훨씬 아프다.
 */
class CallerLookup(private val employees: CallerDirectory) {

    enum class Confidence {
        /** E.164 완전 일치. */
        EXACT,

        /** 뒤 8자리만 일치. 후보가 유일할 때만 인정한다. */
        LOOSE,
    }

    data class Match(val employee: Employee, val confidence: Confidence)

    suspend fun identify(rawNumber: String?): Match? {
        val normalized = PhoneNumberNormalizer.normalize(rawNumber)
        if (!normalized.isUsable) return null

        normalized.e164?.let { e164 ->
            val hits = employees.findByE164(e164)
            // 같은 번호를 두 직원에게 등록한 경우. 편집 실수이므로 첫 번째를 쓴다.
            hits.firstOrNull()?.let { return Match(it, Confidence.EXACT) }
        }

        normalized.loose?.let { loose ->
            val hits = employees.findByLoose(loose)
            // 02-1234-5678 과 010-1234-5678 은 뒤 8자리가 같다.
            // 후보가 둘 이상이면 누구인지 단정할 수 없으니 팝업을 띄우지 않는다.
            val distinct = hits.distinctBy { it.id }
            if (distinct.size == 1) return Match(distinct.first(), Confidence.LOOSE)
        }

        return null
    }

    /** 첫 통화에서 DB 열기 비용을 물지 않도록 미리 데워 둔다. */
    suspend fun warmUp() {
        runCatching { employees.touch() }
    }
}
