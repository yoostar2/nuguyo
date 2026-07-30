package com.nuguyo.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.nuguyo.app.appContainer
import com.nuguyo.app.domain.lookup.CallerLookup
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 문자 수신 팝업.
 *
 * `SMS_RECEIVED` 는 관찰용 브로드캐스트라서 기본 문자앱이 될 필요가 없다
 * (기본 문자앱이 되어야 하는 건 `SMS_DELIVER`/MMS 쪽이다).
 *
 * Android 12+ 는 백그라운드에서 포그라운드 서비스를 시작하는 걸 막지만,
 * `SYSTEM_ALERT_WINDOW` 권한을 가진 앱은 예외로 허용된다. 이 앱은 그 권한이
 * 기능의 전제이므로 정상 경로에서는 문제가 되지 않는다.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }
            .getOrNull()
            ?.filterNotNull()
            ?: return
        if (messages.isEmpty()) return

        val sender = messages.first().originatingAddress ?: return
        // 장문은 여러 조각으로 쪼개져 오므로 본문을 이어 붙인다.
        val body = messages.joinToString(separator = "") { it.messageBody.orEmpty() }

        val container = context.appContainer
        if (!container.settings.smsPopupEnabled) return

        val displayNumber = PhoneNumberNormalizer.format(sender)
        val pending = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val match = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                    runCatching { container.lookup.identify(sender) }.getOrNull()
                }

                runCatching {
                    container.events.record(
                        employeeId = match?.employee?.id,
                        number = displayNumber,
                        kind = ContactKind.SMS,
                        preview = body.take(PREVIEW_LIMIT),
                    )
                }

                if (match == null && !container.settings.showUnknownNumbers) return@launch

                val started = CallerOverlayService.show(
                    context = context,
                    employeeId = match?.employee?.id,
                    number = displayNumber,
                    kind = ContactKind.SMS,
                    preview = body.take(PREVIEW_LIMIT),
                    looseMatch = match?.confidence == CallerLookup.Confidence.LOOSE,
                )
                if (!started) {
                    Log.w(TAG, "오버레이를 띄우지 못해 알림으로 대체한다")
                    Notifications.postCallerFallback(
                        context = context,
                        title = match?.employee?.name ?: displayNumber,
                        body = body.take(PREVIEW_LIMIT),
                    )
                }
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsReceiver"
        const val LOOKUP_TIMEOUT_MS = 3000L
        const val PREVIEW_LIMIT = 200
    }
}
