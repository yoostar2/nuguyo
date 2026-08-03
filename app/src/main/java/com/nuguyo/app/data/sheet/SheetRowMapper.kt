package com.nuguyo.app.data.sheet

import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.EmployeeSource
import com.nuguyo.app.domain.model.StaffNumber
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer

/** 시트 한 행을 읽다가 생긴 문제. 동기화를 멈추지 않고 모아서 보고한다. */
data class SheetRowIssue(val rowNumber: Int, val reason: String)

data class SheetParseResult(
    val employees: List<Employee>,
    val issues: List<SheetRowIssue>,
)

/**
 * 시트의 표를 직원 목록으로 옮긴다.
 *
 * 열 순서를 강제하지 않고 **헤더 이름으로 찾는다.** 사내에서 쓰던 명부 시트에
 * 열이 하나 더 있거나 순서가 다르다고 동기화가 깨지면 쓸 수 없기 때문이다.
 */
object SheetRowMapper {

    private val NAME = setOf("이름", "성명", "name", "fullname")
    private val DEPARTMENT = setOf("부서", "소속", "팀", "department", "dept", "team")
    private val TITLE = setOf("직급", "직책", "직위", "title", "position", "rank")
    private val EMPLOYEE_NO = setOf("사번", "사원번호", "employeeno", "employeenumber", "empno")
    private val TAGS = setOf("태그", "tag", "tags", "라벨", "label")
    private val MEMO = setOf("메모", "비고", "노트", "memo", "note", "notes")
    private val PHONE = setOf(
        "전화번호", "번호", "연락처", "휴대폰", "휴대전화", "핸드폰", "전화", "내선",
        "phone", "mobile", "tel", "number", "contact",
    )

    fun map(rows: List<List<String>>): SheetParseResult {
        if (rows.isEmpty()) {
            return SheetParseResult(emptyList(), listOf(SheetRowIssue(0, "시트가 비어 있습니다")))
        }

        val header = rows.first().map { normalizeHeader(it) }
        val nameColumn = header.indexOfFirst { it in NAME }
        val phoneColumns = header.indices.filter { header[it] in PHONE }

        if (nameColumn < 0) {
            return SheetParseResult(
                emptyList(),
                listOf(SheetRowIssue(1, "'이름' 열을 찾지 못했습니다. 첫 줄이 제목 줄인지 확인하세요")),
            )
        }
        if (phoneColumns.isEmpty()) {
            return SheetParseResult(
                emptyList(),
                listOf(SheetRowIssue(1, "'전화번호' 열을 찾지 못했습니다")),
            )
        }

        val departmentColumn = header.indexOfFirst { it in DEPARTMENT }
        val titleColumn = header.indexOfFirst { it in TITLE }
        val employeeNoColumn = header.indexOfFirst { it in EMPLOYEE_NO }
        val tagsColumn = header.indexOfFirst { it in TAGS }
        val memoColumn = header.indexOfFirst { it in MEMO }

        val employees = mutableListOf<Employee>()
        val issues = mutableListOf<SheetRowIssue>()
        val usedKeys = mutableSetOf<String>()

        rows.drop(1).forEachIndexed { offset, cells ->
            val rowNumber = offset + 2 // 사용자가 시트에서 보는 줄 번호(1행은 제목)
            val name = cells.getOrNull(nameColumn)?.trim().orEmpty()
            if (name.isEmpty()) {
                issues += SheetRowIssue(rowNumber, "이름이 비어 있어 건너뜁니다")
                return@forEachIndexed
            }

            val parsedCells = phoneColumns
                .mapNotNull { cells.getOrNull(it) }
                .map { PhoneCellParser.parse(it) }

            // 칸을 못 알아본 이유는 그대로 보고한다. 조용히 빠지면 시트를 고칠 수 없다.
            parsedCells.flatMap { it.problems }.forEach { problem ->
                issues += SheetRowIssue(rowNumber, "$name — $problem")
            }

            val numbers = parsedCells
                .flatMap { it.numbers }
                .filter { PhoneNumberNormalizer.normalize(it).isUsable }
                .distinctBy { PhoneNumberNormalizer.normalize(it).digits }

            if (numbers.isEmpty()) {
                issues += SheetRowIssue(rowNumber, "$name — 쓸 수 있는 전화번호가 없어 건너뜁니다")
                return@forEachIndexed
            }

            val employeeNo = employeeNoColumn.cell(cells)
            val key = sourceKey(employeeNo, numbers.first(), name)
            if (!usedKeys.add(key)) {
                issues += SheetRowIssue(rowNumber, "$name — 앞 행과 같은 사람으로 보여 건너뜁니다")
                return@forEachIndexed
            }

            employees += Employee(
                id = "",
                name = name,
                department = departmentColumn.cell(cells),
                title = titleColumn.cell(cells),
                employeeNo = employeeNo,
                memo = memoColumn.cell(cells),
                tags = tagsColumn.cell(cells)
                    ?.split(',', ';')
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty(),
                numbers = numbers.map { StaffNumber(id = "", raw = it) },
                source = EmployeeSource.SHEET,
                sourceKey = key,
            )
        }

        return SheetParseResult(employees, issues)
    }

    /**
     * 행을 다시 찾아낼 안정적인 키.
     *
     * 사번이 있으면 그걸 쓴다. 시트에서 이름이나 번호를 고쳐도 같은 사람으로 이어져야
     * 메모나 이력이 끊기지 않는다. 사번이 없으면 번호, 그마저 없으면 이름 순.
     */
    private fun sourceKey(employeeNo: String?, firstNumber: String, name: String): String = when {
        !employeeNo.isNullOrBlank() -> "no:${employeeNo.trim()}"
        else -> {
            val normalized = PhoneNumberNormalizer.normalize(firstNumber)
            normalized.e164?.let { "tel:$it" } ?: "name:$name"
        }
    }

    private fun Int.cell(cells: List<String>): String? =
        if (this < 0) null else cells.getOrNull(this)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * `직급(직위)`, `전화 번호`, `전화번호2` 가 모두 같은 열로 인식되도록 다듬는다.
     * 괄호는 문자만 지우면 `직급직위` 가 되어 오히려 안 맞으므로 통째로 버린다.
     */
    private fun normalizeHeader(raw: String): String =
        raw.trim()
            .lowercase()
            .replace(Regex("""\(.*?\)"""), "")
            .replace(Regex("""[\s_\-.]"""), "")
            .replace(Regex("""\d+$"""), "")
}
