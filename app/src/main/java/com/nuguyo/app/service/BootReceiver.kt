package com.nuguyo.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nuguyo.app.appContainer
import kotlinx.coroutines.launch

/**
 * 재부팅/업데이트 직후 DB 를 열어 둔다. 첫 통화 팝업이 "DB 최초 오픈" 비용까지
 * 함께 물면 벨소리보다 늦게 뜰 수 있다.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                val container = context.appContainer
                container.appScope.launch { container.lookup.warmUp() }
            }
        }
    }
}
