package com.nuguyo.app.service

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.nuguyo.app.appContainer
import com.nuguyo.app.data.sheet.SyncResult
import java.util.concurrent.TimeUnit

/**
 * 주기적으로 구글 시트를 다시 읽는다.
 *
 * 관리자가 시트를 고쳤을 때 각 폰이 알아서 따라와야 한다. 앱을 켜야만 갱신된다면
 * 명부 관리를 시트로 옮긴 의미가 절반은 사라진다.
 */
class SheetSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = applicationContext.appContainer
        if (container.settings.sheetUrl == null) return Result.success()

        return when (container.sheetSync.sync()) {
            is SyncResult.Success -> Result.success()
            // 네트워크가 잠깐 끊긴 것일 수 있으니 다시 시도한다.
            // 시트가 비공개인 경우처럼 되돌릴 수 없는 실패도 섞이지만, 주기가
            // 길어 재시도 비용이 크지 않다.
            is SyncResult.Failure -> Result.retry()
        }
    }

    companion object {
        private const val NAME = "sheet-sync"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SheetSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // 설정을 바꿔 다시 걸 때 주기를 갱신해야 하므로 UPDATE.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }

        /** 설정 상태에 맞춰 예약을 걸거나 푼다. */
        fun apply(context: Context) {
            val settings = context.appContainer.settings
            if (settings.sheetUrl != null && settings.sheetAutoSync) {
                schedule(context)
            } else {
                cancel(context)
            }
        }
    }
}
