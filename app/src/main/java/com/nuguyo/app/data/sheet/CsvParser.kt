package com.nuguyo.app.data.sheet

/**
 * RFC 4180 범위의 CSV 파서.
 *
 * 라이브러리를 넣지 않은 이유: 구글 시트가 내보내는 CSV 는 규격이 일정하고,
 * 우리가 감당해야 하는 건 따옴표 안의 쉼표·줄바꿈·이스케이프 정도다.
 * 대신 그 경우들을 테스트로 못박아 둔다.
 */
object CsvParser {

    fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var index = 0

        // 구글 시트 내보내기는 UTF-8 BOM 을 붙인다. 그대로 두면 첫 헤더가 안 맞는다.
        val source = text.removePrefix("\uFEFF")

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            rows.add(row)
            row = mutableListOf()
        }

        while (index < source.length) {
            val ch = source[index]
            when {
                inQuotes -> when {
                    ch != '"' -> field.append(ch)
                    // "" 는 따옴표 한 개를 뜻한다.
                    index + 1 < source.length && source[index + 1] == '"' -> {
                        field.append('"')
                        index++
                    }
                    else -> inQuotes = false
                }

                ch == '"' -> inQuotes = true
                ch == ',' -> endField()
                ch == '\r' -> Unit // CRLF 는 \n 에서 한 번만 처리한다.
                ch == '\n' -> endRow()
                else -> field.append(ch)
            }
            index++
        }
        // 마지막 줄에 개행이 없을 수 있다.
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()

        // 시트 아래쪽의 빈 행은 버린다.
        return rows.filter { cells -> cells.any { it.isNotBlank() } }
    }
}
