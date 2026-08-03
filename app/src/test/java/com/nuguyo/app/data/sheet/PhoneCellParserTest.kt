package com.nuguyo.app.data.sheet

import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 시트에 사람이 손으로 적어 넣는 칸이라 표기가 제멋대로다.
 * 어떤 구분자가 들어와도 같은 번호로 읽히는지가 이 클래스의 전부다.
 */
class PhoneCellParserTest {

    /** 파싱 결과를 E.164 로 바꿔 비교한다. 표기가 아니라 어떤 번호인지가 중요하다. */
    private fun e164(cell: String): List<String?> =
        PhoneCellParser.parse(cell).numbers.map { PhoneNumberNormalizer.normalize(it).e164 }

    @Test
    fun `구분자가 무엇이든 한 번호는 한 번호로 읽는다`() {
        val expected = listOf("+821012341234")

        assertEquals(expected, e164("01012341234"))
        assertEquals(expected, e164("010-1234-1234"))
        assertEquals(expected, e164("010,1234,1234"))
        assertEquals(expected, e164("010.1234.1234"))
        assertEquals(expected, e164("010 1234 1234"))
        assertEquals(expected, e164("(010) 1234-1234"))
        assertEquals(expected, e164("010_1234_1234"))
        assertEquals(expected, e164("  010-1234-1234  "))
    }

    @Test
    fun `국가번호 표기도 한 번호로 읽는다`() {
        assertEquals(listOf("+821012341234"), e164("+82 10-1234-1234"))
        assertEquals(listOf("+821012341234"), e164("+82 (0)10 1234 1234"))
        assertEquals(listOf("+821012341234"), e164("0082-10-1234-1234"))
    }

    @Test
    fun `시트가 앞자리 0 을 떼어 먹어도 같은 번호로 읽는다`() {
        // 셀 서식이 숫자면 구글 시트가 01012341234 를 1012341234 로 저장한다.
        assertEquals(listOf("+821012341234"), e164("1012341234"))
        assertEquals(listOf("+82212345678"), e164("212345678"))
    }

    @Test
    fun `번호를 여러 개 적으면 나눠 읽는다`() {
        val two = listOf("+821011112222", "+821033334444")

        assertEquals(two, e164("010-1111-2222, 010-3333-4444"))
        assertEquals(two, e164("010-1111-2222; 010-3333-4444"))
        assertEquals(two, e164("010-1111-2222 / 010-3333-4444"))
        assertEquals(two, e164("010-1111-2222\n010-3333-4444"))
        assertEquals(two, e164("010-1111-2222 010-3333-4444"))
    }

    @Test
    fun `자릿수 구분과 번호 구분에 쉼표가 같이 쓰여도 풀어낸다`() {
        // 여기서 쉼표로 먼저 쪼개면 010 / 1111 / 2222 … 로 부서진다.
        // 쪼갠 조각이 전부 멀쩡한 번호일 때만 그 해석을 받아들여야 한다.
        assertEquals(
            listOf("+821011112222", "+821033334444"),
            e164("010,1111,2222 010,3333,4444"),
        )
    }

    @Test
    fun `유선과 대표번호도 섞어 쓸 수 있다`() {
        assertEquals(
            listOf("+821012341234", "+82221234567", "+8215881234"),
            e164("010-1234-1234; 02-123-4567; 1588-1234"),
        )
    }

    @Test
    fun `같은 번호를 다르게 적어 두면 한 번만 남긴다`() {
        assertEquals(listOf("+821012341234"), e164("010-1234-1234, 01012341234"))
    }

    @Test
    fun `전각 숫자와 전각 기호도 읽는다`() {
        assertEquals(listOf("+821012341234"), e164("０１０-1234-1234"))
        assertEquals(listOf("+821012341234"), e164("010，1234，1234"))
    }

    @Test
    fun `줄바꿈 없는 공백과 폭 없는 문자를 걷어낸다`() {
        // 웹에서 붙여넣은 값에 자주 섞여 들어온다. 눈에 보이지 않아 더 골치 아프다.
        assertEquals(listOf("+821012341234"), e164("010\u00A01234\u00A01234"))
        assertEquals(listOf("+821012341234"), e164("010-1234-1234\u200B"))
        assertEquals(listOf("+821012341234"), e164("\u3000010-1234-1234"))
    }

    @Test
    fun `빈 칸은 조용히 넘어간다`() {
        assertEquals(emptyList<String>(), PhoneCellParser.parse("").numbers)
        assertEquals(emptyList<String>(), PhoneCellParser.parse("   ").numbers)
        assertTrue(PhoneCellParser.parse("").problems.isEmpty())
    }

    @Test
    fun `숫자 서식으로 깨진 지수 표기는 추측하지 않고 알려 준다`() {
        // 1.01234E+10 은 이미 뒷자리가 날아가서 복구할 수 없다.
        // 그럴듯한 번호를 지어내면 엉뚱한 사람에게 전화가 걸린다.
        val result = PhoneCellParser.parse("1.01234E+10")

        assertTrue(result.numbers.isEmpty())
        assertTrue(result.problems.single().contains("일반 텍스트"))
    }

    @Test
    fun `너무 길어서 나눌 수도 없는 칸은 버리고 알려 준다`() {
        val result = PhoneCellParser.parse("01012341234561234567890")

        assertTrue(result.numbers.isEmpty())
        assertTrue(result.problems.single().contains("알아볼 수 없습니다"))
    }

    @Test
    fun `사내 내선처럼 짧은 번호도 통과시킨다`() {
        assertEquals(listOf("1234"), PhoneCellParser.parse("1234").numbers)
    }
}
