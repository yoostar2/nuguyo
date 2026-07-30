package com.nuguyo.app.ui.editor

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
            val directory = File(context.filesDir, "photos").apply { mkdirs() }
            val target = File(directory, "${UUID.randomUUID()}.jpg")
            val copied = context.contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
                true
            } ?: false
            if (copied) Uri.fromFile(target) else null
        }.getOrNull()
    }

/** 사진을 바꾸거나 지울 때 이전 파일을 남기지 않는다. */
fun deleteAppStoragePhoto(uri: String?) {
    val path = uri?.removePrefix("file://") ?: return
    if (!path.contains("/photos/")) return
    runCatching { File(path).delete() }
}
