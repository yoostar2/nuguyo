package com.nuguyo.app.domain.model

/** 이 직원 정보가 어디서 왔는지. 동기화가 무엇을 지워도 되는지를 가른다. */
enum class EmployeeSource {
    /** 앱에서 직접 만든 직원. 시트 동기화가 건드리지 않는다. */
    LOCAL,

    /** 구글 시트에서 온 직원. 시트가 원본이라 동기화 때 덮어쓰고, 사라지면 지운다. */
    SHEET,
}

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
    val source: EmployeeSource = EmployeeSource.LOCAL,
    /** 시트 행을 다시 찾아낼 안정적인 키. LOCAL 이면 null. */
    val sourceKey: String? = null,
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
