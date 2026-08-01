package com.nuguyo.app.data.sheet

/**
 * 사용자가 붙여넣은 구글 시트 주소를 CSV 내보내기 주소로 바꾼다.
 *
 * 주소창에서 복사한 편집 링크, 공유 링크, "웹에 게시" 링크가 전부 들어올 수 있다.
 * 어떤 걸 붙여넣어도 되게 만드는 편이, 사용자에게 특정 형식을 요구하는 것보다 낫다.
 */
object GoogleSheetUrl {

    /** `/spreadsheets/d/<ID>` 또는 게시 링크의 `/spreadsheets/d/e/<ID>` */
    private val ID = Regex("""/spreadsheets/d/(e/)?([a-zA-Z0-9\-_]+)""")

    /** `#gid=0`, `?gid=0`, `&gid=0` 모두 대응. */
    private val GID = Regex("""[#?&]gid=(\d+)""")

    /** 링크가 아니라 스프레드시트 ID 만 붙여넣은 경우. */
    private val BARE_ID = Regex("""^[a-zA-Z0-9\-_]{20,}$""")

    /**
     * @return 익명으로 받을 수 있는 CSV 주소. 구글 시트 주소로 보이지 않으면 null.
     */
    fun toCsvUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        if (BARE_ID.matches(trimmed)) {
            return "https://docs.google.com/spreadsheets/d/$trimmed/export?format=csv"
        }
        if (!trimmed.startsWith("http")) return null

        val match = ID.find(trimmed) ?: return null
        val isPublished = match.groupValues[1].isNotEmpty()
        val id = match.groupValues[2]
        val gid = GID.find(trimmed)?.groupValues?.get(1)

        // "웹에 게시"로 만든 링크(/d/e/…)는 export 엔드포인트가 없다. pub 을 써야 한다.
        if (isPublished) {
            val base = "https://docs.google.com/spreadsheets/d/e/$id/pub?output=csv"
            return if (gid != null) "$base&gid=$gid" else base
        }

        val base = "https://docs.google.com/spreadsheets/d/$id/export?format=csv"
        return if (gid != null) "$base&gid=$gid" else base
    }

    /** 사용자에게 다시 보여줄 원본 시트 주소(편집 화면). */
    fun toSheetUrl(input: String): String? {
        val match = ID.find(input.trim()) ?: return null
        if (match.groupValues[1].isNotEmpty()) return null
        return "https://docs.google.com/spreadsheets/d/${match.groupValues[2]}/edit"
    }
}
