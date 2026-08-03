package com.nuguyo.app.data.sheet

import com.nuguyo.app.domain.phone.PhoneNumberNormalizer

/**
 * 시트의 전화번호 칸 하나를 번호 목록으로 푼다.
 *
 * 어려운 점은 쉼표가 두 가지로 쓰인다는 것이다. `010,1234,1234` 처럼 자릿수를
 * 끊는 용도로도 쓰이고, `010-1111-2222, 010-3333-4444` 처럼 번호를 나누는
 * 용도로도 쓰인다. 그래서 구분자만 보고 무조건 쪼개면 안 된다.
 *
 * 대신 **자릿수로 판단한다.** 칸 전체의 숫자가 번호 하나에 들어갈 만한 길이면
 * 구분자가 뭐가 됐든 한 개로 보고, 그보다 길면 그때 나눠 본다.
 */
object PhoneCellParser {

    data class Result(
        val numbers: List<String>,
        /** 사용자에게 알려 줄 문제. 시트를 고쳐야 하는 경우에만 채워진다. */
        val problems: List<String> = emptyList(),
    )

    /** 번호 하나에 들어갈 수 있는 최대 자릿수(E.164 상한). 이보다 길면 여러 개로 본다. */
    private const val MAX_SINGLE_DIGITS = 15

    /** 번호 안에는 절대 쓰이지 않는 구분자. 자릿수와 무관하게 항상 나눈다. */
    private val HARD_SEPARATORS = Regex("""[\n\r;|/]""")

    /** 번호 안에도 쓰일 수 있는 구분자. 나눈 결과가 전부 멀쩡할 때만 인정한다. */
    private val SOFT_SEPARATORS = listOf(Regex(","), Regex("""\s+"""))

    /** 시트가 긴 숫자를 지수 표기로 바꿔 버린 경우. 자릿수가 이미 날아가 복구할 수 없다. */
    private val SCIENTIFIC = Regex("""^\d(\.\d+)?[eE][+\-]?\d+$""")

    fun parse(cell: String): Result {
        val cleaned = cleanUp(cell)
        if (cleaned.isBlank()) return Result(emptyList())

        if (SCIENTIFIC.matches(cleaned)) {
            return Result(
                emptyList(),
                listOf(
                    "\"$cleaned\" — 시트가 번호를 숫자로 인식해 자릿수가 깨졌습니다. " +
                        "해당 열의 서식을 '일반 텍스트'로 바꾸세요",
                ),
            )
        }

        val numbers = mutableListOf<String>()
        val problems = mutableListOf<String>()

        cleaned.split(HARD_SEPARATORS)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { chunk ->
                val split = splitIfTooLong(chunk)
                if (split == null) {
                    problems += "\"$chunk\" — 번호를 알아볼 수 없습니다"
                } else {
                    numbers += split
                }
            }

        return Result(
            numbers = numbers.distinctBy { PhoneNumberNormalizer.normalize(it).digits },
            problems = problems,
        )
    }

    /**
     * @return 한 덩어리면 그대로, 여러 개면 나눈 목록, 도저히 해석이 안 되면 null.
     */
    private fun splitIfTooLong(chunk: String): List<String>? {
        if (digitCount(chunk) <= MAX_SINGLE_DIGITS) {
            // `010,1234,1234`, `010.1234.1234`, `010 1234 1234` 전부 여기로 온다.
            // 정규화가 숫자만 남기므로 구분자가 무엇이든 상관없다.
            return listOf(chunk)
        }

        for (separator in SOFT_SEPARATORS) {
            val parts = chunk.split(separator).map { it.trim() }.filter { it.isNotEmpty() }
            // 나눈 조각이 전부 걸 수 있는 번호일 때만 그 해석을 받아들인다.
            // 그렇지 않으면 `010,1234,1234` 를 세 조각으로 부수게 된다.
            if (parts.size > 1 && parts.all { PhoneNumberNormalizer.normalize(it).e164 != null }) {
                return parts
            }
        }
        return null
    }

    private fun digitCount(text: String): Int = text.count { it.isDigit() }

    /** 전각 숫자·전각 쉼표·줄바꿈 없는 공백·폭 없는 문자를 평범한 문자로 되돌린다. */
    private fun cleanUp(cell: String): String = buildString {
        cell.forEach { ch ->
            when (ch) {
                // 전각 숫자 \uFF10-\uFF19 를 ASCII 로.
                in '\uFF10'..'\uFF19' -> append(ch - 0xFEE0)
                '\uFF0C' -> append(',')                       // 전각 쉼표
                '\uFF0D', '\u2010', '\u2013', '\u2014' -> append('-') // 전각/유니코드 하이픈
                '\u00A0', '\u3000' -> append(' ')            // 줄바꿈 없는 공백, 전각 공백
                '\u200B', '\uFEFF' -> Unit                   // 폭 없는 문자는 버린다
                else -> append(ch)
            }
        }
    }.trim()
}
