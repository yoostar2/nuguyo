package com.nuguyo.app.domain.model

/** 직원 한 명. 전화번호는 여러 개 가질 수 있다(휴대폰/사내/직통). */
data class Employee(
    val id: String,
    val name: String,
    val department: String? = null,
    val title: String? = null,
    val employeeNo: String? = null,
    val photoUri: String? = null,
    val memo: String? = null,
    val tags: List<String> = emptyList(),
    val numbers: List<StaffNumber> = emptyList(),
    val updatedAt: Long = 0L,
) {
    /** "영업2팀 · 과장" 처럼 부제로 쓸 한 줄. */
    val subtitle: String
        get() = listOfNotNull(
            department?.takeIf { it.isNotBlank() },
            title?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")

    val initial: String
        get() = name.trim().firstOrNull()?.toString() ?: "?"
}

/**
 * @param raw 사용자가 입력한 원본 표기. 화면에는 이걸 보여준다.
 * @param e164 매칭용 정규화 키. 자세한 규칙은 [com.nuguyo.app.domain.phone.PhoneNumberNormalizer].
 * @param loose 보조 매칭 키(뒤 8자리).
 */
data class StaffNumber(
    val id: String,
    val raw: String,
    val e164: String? = null,
    val loose: String? = null,
    val label: String? = null,
)

enum class ContactKind { CALL, SMS }

data class ContactEvent(
    val id: String,
    val employeeId: String?,
    val number: String,
    val kind: ContactKind,
    val at: Long,
    val preview: String? = null,
)

/** 팝업 하단에 뿌리는 "최근 N회 · 마지막 통화" 요약. */
data class ContactSummary(
    val totalCount: Int = 0,
    val lastContactAt: Long? = null,
)
