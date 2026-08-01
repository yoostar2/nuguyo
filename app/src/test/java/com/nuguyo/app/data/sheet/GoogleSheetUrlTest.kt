package com.nuguyo.app.data.sheet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GoogleSheetUrlTest {

    private val id = "1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms"

    @Test
    fun `주소창에서 복사한 편집 링크를 CSV 주소로 바꾼다`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=0",
            GoogleSheetUrl.toCsvUrl("https://docs.google.com/spreadsheets/d/$id/edit#gid=0"),
        )
    }

    @Test
    fun `공유 링크의 쿼리 파라미터도 처리한다`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$id/export?format=csv&gid=1234567",
            GoogleSheetUrl.toCsvUrl(
                "https://docs.google.com/spreadsheets/d/$id/edit?usp=sharing&gid=1234567#gid=1234567",
            ),
        )
    }

    @Test
    fun `시트 번호가 없으면 첫 번째 시트를 받는다`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$id/export?format=csv",
            GoogleSheetUrl.toCsvUrl("https://docs.google.com/spreadsheets/d/$id/edit"),
        )
    }

    @Test
    fun `웹에 게시한 링크는 pub 엔드포인트를 쓴다`() {
        // /d/e/ 형태에는 export 가 없어서 edit 링크와 같은 규칙을 쓰면 404 가 난다.
        val publishedId = "2PACX-1vQx7Yb0Zc9Kk"
        assertEquals(
            "https://docs.google.com/spreadsheets/d/e/$publishedId/pub?output=csv",
            GoogleSheetUrl.toCsvUrl(
                "https://docs.google.com/spreadsheets/d/e/$publishedId/pubhtml",
            ),
        )
    }

    @Test
    fun `스프레드시트 ID 만 붙여넣어도 받아준다`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$id/export?format=csv",
            GoogleSheetUrl.toCsvUrl(id),
        )
    }

    @Test
    fun `앞뒤 공백을 무시한다`() {
        assertEquals(
            "https://docs.google.com/spreadsheets/d/$id/export?format=csv",
            GoogleSheetUrl.toCsvUrl("  https://docs.google.com/spreadsheets/d/$id/edit  "),
        )
    }

    @Test
    fun `구글 시트가 아니면 거절한다`() {
        assertNull(GoogleSheetUrl.toCsvUrl(""))
        assertNull(GoogleSheetUrl.toCsvUrl("여기에 링크"))
        assertNull(GoogleSheetUrl.toCsvUrl("https://example.com/sheet.csv"))
        assertNull(GoogleSheetUrl.toCsvUrl("https://docs.google.com/document/d/$id/edit"))
    }
}
