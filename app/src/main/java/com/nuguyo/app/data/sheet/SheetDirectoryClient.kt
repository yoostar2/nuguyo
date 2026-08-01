package com.nuguyo.app.data.sheet

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** 사용자에게 그대로 보여줄 수 있는 실패 사유. */
class SheetFetchException(message: String) : IOException(message)

/**
 * 공개 링크로 열려 있는 구글 시트의 CSV 를 받아온다.
 *
 * OkHttp 를 넣지 않은 이유: 요청이 딱 하나고 GET 뿐이다. 나중에 OAuth 방식으로
 * 올릴 때 갈아끼울 지점을 이 클래스 하나로 좁혀 두는 쪽이 더 중요하다.
 */
class SheetDirectoryClient {

    suspend fun fetchCsv(csvUrl: String): String = withContext(Dispatchers.IO) {
        var url = URL(csvUrl)

        repeat(MAX_REDIRECTS + 1) {
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                // 구글은 export 요청을 googleusercontent 로 넘긴다. 호스트가 바뀌는
                // 리다이렉트는 자동으로 따라가지 않으므로 직접 처리한다.
                instanceFollowRedirects = false
                setRequestProperty("Accept", "text/csv,text/plain,*/*")
            }
            try {
                val status = connection.responseCode

                if (status in 300..399) {
                    val location = connection.getHeaderField("Location")
                        ?: throw SheetFetchException("시트 주소가 잘못된 곳으로 연결됩니다")
                    url = URL(url, location)
                    return@repeat
                }
                if (status == HttpURLConnection.HTTP_UNAUTHORIZED ||
                    status == HttpURLConnection.HTTP_FORBIDDEN
                ) {
                    throw SheetFetchException(NOT_PUBLIC)
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    throw SheetFetchException("시트를 불러오지 못했습니다 (HTTP $status)")
                }

                val body = connection.inputStream
                    .use { it.readLimited(MAX_BYTES) }
                    .toString(Charsets.UTF_8)

                // 공개되지 않은 시트는 오류 코드 대신 로그인 페이지(HTML)를 돌려준다.
                val looksLikeHtml =
                    connection.contentType?.contains("html", ignoreCase = true) == true ||
                        body.trimStart().startsWith("<")
                if (looksLikeHtml) throw SheetFetchException(NOT_PUBLIC)

                return@withContext body
            } finally {
                connection.disconnect()
            }
        }

        throw SheetFetchException("시트 주소가 계속 다른 곳으로 연결됩니다")
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val buffer = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        while (true) {
            val read = read(chunk)
            if (read < 0) break
            buffer.write(chunk, 0, read)
            if (buffer.size() > limit) {
                throw SheetFetchException("시트가 너무 큽니다 (${limit / 1024 / 1024}MB 초과)")
            }
        }
        return buffer.toByteArray()
    }

    private companion object {
        const val TIMEOUT_MS = 15_000
        const val MAX_REDIRECTS = 5
        const val MAX_BYTES = 5 * 1024 * 1024

        const val NOT_PUBLIC =
            "시트가 공개 상태가 아닙니다. 공유 > '링크가 있는 모든 사용자'를 뷰어로 설정하세요"
    }
}
