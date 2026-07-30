package com.nuguyo.app.service

import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import com.nuguyo.app.appContainer
import com.nuguyo.app.domain.lookup.CallerLookup
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 발신자 식별의 진입점.
 *
 * 이 서비스가 호출되려면 앱이 `RoleManager.ROLE_CALL_SCREENING`("발신자 표시 및 스팸 앱")
 * 역할을 보유해야 한다. 역할은 기기당 한 앱만 가질 수 있어서 삼성 스팸차단이나
 * 후스콜 등과는 동시에 쓸 수 없다.
 *
 * Android 10 부터 `PHONE_STATE` 브로드캐스트에는 발신번호가 실리지 않는다.
 * READ_CALL_LOG 없이 번호를 알 수 있는 정식 경로는 여기뿐이다.
 */
class StaffCallScreeningService : CallScreeningService() {

    override fun onScreenCall(callDetails: Call.Details) {
        // 무엇보다 먼저 통화를 통과시킨다. 우리 역할은 차단이 아니라 식별이고,
        // 응답이 늦으면 사용자 전화가 늦게 울린다.
        respondToCall(callDetails, CallResponse.Builder().build())

        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) return

        val container = appContainer
        if (!container.settings.callPopupEnabled) return

        val rawNumber = callDetails.handle?.schemeSpecificPart
        val displayNumber = PhoneNumberNormalizer.format(rawNumber).ifBlank { UNKNOWN_LABEL }

        // 응답은 이미 끝냈고 남은 일은 색인 조회 두 번이다. 여기서 결과를 기다리는 편이
        // 미등록 번호마다 포그라운드 서비스를 띄웠다 끄는 것보다 낫다.
        val match = runBlocking {
            withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                runCatching { container.lookup.identify(rawNumber) }.getOrNull()
            }
        }

        container.appScope.launch {
            runCatching {
                container.events.record(
                    employeeId = match?.employee?.id,
                    number = displayNumber,
                    kind = ContactKind.CALL,
                )
            }
        }

        if (match == null && !container.settings.showUnknownNumbers) return

        val started = CallerOverlayService.show(
            context = this,
            employeeId = match?.employee?.id,
            number = displayNumber,
            kind = ContactKind.CALL,
            looseMatch = match?.confidence == CallerLookup.Confidence.LOOSE,
        )
        if (!started) {
            // 오버레이 경로가 막혔다(권한 없음/백그라운드 제한). 알림으로라도 알린다.
            Log.w(TAG, "오버레이 서비스를 시작하지 못해 알림으로 대체한다")
            Notifications.postCallerFallback(
                context = this,
                title = match?.employee?.name ?: UNKNOWN_LABEL,
                body = listOfNotNull(match?.employee?.subtitle?.takeIf { it.isNotBlank() }, displayNumber)
                    .joinToString(" · "),
            )
        }
    }

    private companion object {
        const val TAG = "CallScreening"
        const val UNKNOWN_LABEL = "번호 미표시"

        /**
         * 시스템이 onScreenCall 응답을 기다리는 예산은 5초다. 이미 응답했으므로
         * 여유가 있지만, DB 가 잠겨 있는 등의 사고로 콜백이 붙잡히지 않게 잘라 둔다.
         */
        const val LOOKUP_TIMEOUT_MS = 1500L
    }
}
