package com.nuguyo.app.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `기본 표를 행과 칸으로 나눈다`() {
        val parsed = CsvParser.parse("이름,부서\n김서연,영업2팀\n박민준,총무팀\n")

        assertEquals(
            listOf(
                listOf("이름", "부서"),
                listOf("김서연", "영업2팀"),
                listOf("박민준", "총무팀"),
            ),
            parsed,
        )
    }

    @Test
    fun `따옴표 안의 쉼표는 칸을 나누지 않는다`() {
        val parsed = CsvParser.parse("이름,메모\n김서연,\"세종점, 청주점 담당\"")

        assertEquals(listOf("김서연", "세종점, 청주점 담당"), parsed[1])
    }

    @Test
    fun `따옴표 안의 줄바꿈은 같은 칸으로 남는다`() {
        val parsed = CsvParser.parse("이름,메모\n김서연,\"1층 안내\n2층 창고\"\n박민준,없음")

        assertEquals(3, parsed.size)
        assertEquals("1층 안내\n2층 창고", parsed[1][1])
        assertEquals("박민준", parsed[2][0])
    }

    @Test
    fun `두 번 겹친 따옴표는 따옴표 한 개다`() {
        val parsed = CsvParser.parse("이름,메모\n김서연,\"별명은 \"\"서연쌤\"\"\"")

        assertEquals("별명은 \"서연쌤\"", parsed[1][1])
    }

    @Test
    fun `CRLF 줄바꿈도 처리한다`() {
        val parsed = CsvParser.parse("이름,부서\r\n김서연,영업2팀\r\n")

        assertEquals(listOf(listOf("이름", "부서"), listOf("김서연", "영업2팀")), parsed)
    }

    @Test
    fun `구글 시트가 붙이는 BOM 때문에 첫 헤더가 깨지지 않는다`() {
        val parsed = CsvParser.parse("\uFEFF이름,부서\n김서연,영업2팀")

        assertEquals("이름", parsed[0][0])
    }

    @Test
    fun `빈 칸과 마지막 개행이 없는 줄을 견딘다`() {
        val parsed = CsvParser.parse("이름,부서,직급\n김서연,,과장")

        assertEquals(listOf("김서연", "", "과장"), parsed[1])
    }

    @Test
    fun `시트 아래쪽의 빈 행은 버린다`() {
        val parsed = CsvParser.parse("이름,부서\n김서연,영업2팀\n,\n,\n")

        assertEquals(2, parsed.size)
    }

    @Test
    fun `빈 입력은 빈 결과다`() {
        assertEquals(emptyList<List<String>>(), CsvParser.parse(""))
        assertEquals(emptyList<List<String>>(), CsvParser.parse("\n\n"))
    }
}
