package com.nuguyo.app.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * 사진 선택기(PickVisualMedia)가 주는 URI 는 영구 권한을 잡을 수 없다.
 * 팝업은 통화가 올 때 서비스에서 이미지를 다시 읽어야 하므로,
 * 고른 사진을 앱 내부 저장소로 복사해 두고 그 경로를 기억한다.
 */
suspend fun copyPhotoToAppStorage(context: Context, source: Uri): Uri? =
    withContext(Dispatchers.IO) {
        runCatching {
            val directory = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            val target = File(directory, "${UUID.randomUUID()}.jpg")
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied) Uri.fromFile(target) else null
        }.getOrNull()
    }

/**
 * 직원을 지우거나 사진을 바꿀 때 파일도 함께 치운다.
 * 그러지 않으면 지운 직원의 사진이 저장소에 계속 쌓인다.
 */
fun deleteAppStoragePhoto(uri: String?) {
    val path = uri?.removePrefix("file://") ?: return
    // 앱이 만든 파일만 건드린다. 다른 경로가 들어오면 무시.
    if (!path.contains("/$PHOTO_DIR/")) return
    runCatching { File(path).delete() }
}

fun deleteAppStoragePhotos(uris: Collection<String?>) = uris.forEach(::deleteAppStoragePhoto)

private const val PHOTO_DIR = "photos"
