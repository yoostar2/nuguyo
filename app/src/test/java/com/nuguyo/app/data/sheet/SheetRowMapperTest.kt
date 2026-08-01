package com.nuguyo.app.data.sheet

import com.nuguyo.app.domain.model.EmployeeSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SheetRowMapperTest {

    private fun map(csv: String) = SheetRowMapper.map(CsvParser.parse(csv))

    @Test
    fun `제목 줄 이름으로 열을 찾는다`() {
        val result = map(
            """
            이름,부서,직급,사번,전화번호,태그,메모
            김서연,영업2팀,과장,20180412,010-2967-1626,주요고객,세종점 담당
            """.trimIndent(),
        )

        assertEquals(1, result.employees.size)
        val employee = result.employees.first()
        assertEquals("김서연", employee.name)
        assertEquals("영업2팀", employee.department)
        assertEquals("과장", employee.title)
        assertEquals("20180412", employee.employeeNo)
        assertEquals(listOf("주요고객"), employee.tags)
        assertEquals("세종점 담당", employee.memo)
        assertEquals("010-2967-1626", employee.numbers.single().raw)
        assertEquals(EmployeeSource.SHEET, employee.source)
    }

    @Test
    fun `열 순서가 달라도 되고 없는 열은 비워 둔다`() {
        val result = map(
            """
            전화번호,이름
            01029671626,김서연
            """.trimIndent(),
        )

        val employee = result.employees.single()
        assertEquals("김서연", employee.name)
        assertEquals(null, employee.department)
    }

    @Test
    fun `영문 제목 줄도 알아본다`() {
        val result = map(
            """
            Name,Department,Phone
            김서연,영업2팀,01029671626
            """.trimIndent(),
        )

        assertEquals("영업2팀", result.employees.single().department)
    }

    @Test
    fun `번호를 한 칸에 여러 개 적어도 되고 열을 나눠도 된다`() {
        val manyInOneCell = map(
            """
            이름,전화번호
            김서연,"010-2967-1626; 02-555-1234"
            """.trimIndent(),
        )
        assertEquals(2, manyInOneCell.employees.single().numbers.size)

        val manyColumns = map(
            """
            이름,전화번호1,전화번호2
            김서연,010-2967-1626,02-555-1234
            """.trimIndent(),
        )
        assertEquals(2, manyColumns.employees.single().numbers.size)
    }

    @Test
    fun `사번이 있으면 이름이나 번호가 바뀌어도 같은 사람으로 이어진다`() {
        val before = map("이름,사번,전화번호\n김서연,20180412,010-2967-1626").employees.single()
        val after = map("이름,사번,전화번호\n김서연A,20180412,010-1111-2222").employees.single()

        assertEquals(before.sourceKey, after.sourceKey)
    }

    @Test
    fun `사번이 없으면 번호로 이어지고 부서를 고쳐도 유지된다`() {
        val before = map("이름,부서,전화번호\n김서연,영업2팀,010-2967-1626").employees.single()
        val after = map("이름,부서,전화번호\n김서연,영업3팀,01029671626").employees.single()

        assertEquals(before.sourceKey, after.sourceKey)
    }

    @Test
    fun `이름이나 쓸 수 있는 번호가 없는 줄은 건너뛰고 이유를 남긴다`() {
        val result = map(
            """
            이름,전화번호
            김서연,010-2967-1626
            ,010-1111-2222
            박민준,
            최지호,119
            """.trimIndent(),
        )

        assertEquals(listOf("김서연"), result.employees.map { it.name })
        assertEquals(3, result.issues.size)
        // 사용자가 시트에서 보는 줄 번호로 알려 줘야 고칠 수 있다.
        assertEquals(listOf(3, 4, 5), result.issues.map { it.rowNumber })
    }

    @Test
    fun `같은 사람이 두 번 적혀 있으면 한 번만 넣는다`() {
        val result = map(
            """
            이름,사번,전화번호
            김서연,20180412,010-2967-1626
            김서연,20180412,010-2967-1626
            """.trimIndent(),
        )

        assertEquals(1, result.employees.size)
        assertEquals(1, result.issues.size)
    }

    @Test
    fun `제목 줄에 필수 열이 없으면 아무것도 넣지 않고 이유를 알려 준다`() {
        val noName = map("부서,전화번호\n영업2팀,010-2967-1626")
        assertTrue(noName.employees.isEmpty())
        assertTrue(noName.issues.single().reason.contains("이름"))

        val noPhone = map("이름,부서\n김서연,영업2팀")
        assertTrue(noPhone.employees.isEmpty())
        assertTrue(noPhone.issues.single().reason.contains("전화번호"))
    }

    @Test
    fun `제목 줄의 공백과 괄호를 무시한다`() {
        val result = map(
            """
            성 명,연 락 처,직급(직위)
            김서연,010-2967-1626,과장
            """.trimIndent(),
        )

        assertEquals("과장", result.employees.single().title)
    }
}
