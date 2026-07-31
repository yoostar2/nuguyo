package com.nuguyo.app.data

import android.content.Context
import android.content.SharedPreferences

/**
 * 동작 설정. 전화가 울리는 경로에서 읽히므로 코루틴 없이 즉시 읽을 수 있어야 한다
 * (그래서 DataStore 가 아니라 SharedPreferences 다).
 */
class SettingsStore(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("nuguyo_settings", Context.MODE_PRIVATE)

    /** 명부에 없는 번호도 "미등록" 팝업으로 알릴지. */
    var showUnknownNumbers: Boolean
        get() = prefs.getBoolean(KEY_SHOW_UNKNOWN, false)
        set(value) = prefs.edit().putBoolean(KEY_SHOW_UNKNOWN, value).apply()

    /** 전화를 받으면 팝업을 닫을지. 끄면 통화 중에도 메모를 계속 볼 수 있다. */
    var dismissOnAnswer: Boolean
        get() = prefs.getBoolean(KEY_DISMISS_ON_ANSWER, true)
        set(value) = prefs.edit().putBoolean(KEY_DISMISS_ON_ANSWER, value).apply()

    /** 문자 팝업이 저절로 사라지기까지의 시간(초). */
    var smsPopupSeconds: Int
        get() = prefs.getInt(KEY_SMS_SECONDS, 12)
        set(value) = prefs.edit().putInt(KEY_SMS_SECONDS, value.coerceIn(3, 60)).apply()

    var callPopupEnabled: Boolean
        get() = prefs.getBoolean(KEY_CALL_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CALL_ENABLED, value).apply()

    var smsPopupEnabled: Boolean
        get() = prefs.getBoolean(KEY_SMS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SMS_ENABLED, value).apply()

    /** 사용자가 팝업을 끌어다 놓은 세로 위치를 기억한다. */
    var popupOffsetY: Int
        get() = prefs.getInt(KEY_POPUP_Y, 0)
        set(value) = prefs.edit().putInt(KEY_POPUP_Y, value).apply()

    /**
     * 마지막으로 통화 스크리닝 콜백을 받은 기록.
     *
     * "역할을 못 받아서 콜백 자체가 안 온 것"과 "콜백은 왔는데 명부 매칭에 실패한 것"은
     * 사용자 눈에는 똑같이 "팝업이 안 뜬다"로 보인다. 그 둘을 가르려면 콜백이
     * 들어온 사실 자체를 남겨 두는 수밖에 없다.
     */
    val lastScreening: ScreeningTrace?
        get() {
            val at = prefs.getLong(KEY_SCREENING_AT, 0L)
            if (at == 0L) return null
            return ScreeningTrace(
                at = at,
                number = prefs.getString(KEY_SCREENING_NUMBER, null).orEmpty(),
                outcome = prefs.getString(KEY_SCREENING_OUTCOME, null).orEmpty(),
            )
        }

    fun recordScreening(number: String, outcome: String) {
        prefs.edit()
            .putLong(KEY_SCREENING_AT, System.currentTimeMillis())
            .putString(KEY_SCREENING_NUMBER, number)
            .putString(KEY_SCREENING_OUTCOME, outcome)
            .apply()
    }

    private companion object {
        const val KEY_SHOW_UNKNOWN = "show_unknown"
        const val KEY_DISMISS_ON_ANSWER = "dismiss_on_answer"
        const val KEY_SMS_SECONDS = "sms_seconds"
        const val KEY_CALL_ENABLED = "call_enabled"
        const val KEY_SMS_ENABLED = "sms_enabled"
        const val KEY_POPUP_Y = "popup_y"

        const val KEY_SCREENING_AT = "screening_at"
        const val KEY_SCREENING_NUMBER = "screening_number"
        const val KEY_SCREENING_OUTCOME = "screening_outcome"
    }
}

/** 마지막 통화 스크리닝 콜백의 흔적. 진단 화면에 그대로 표시한다. */
data class ScreeningTrace(
    val at: Long,
    val number: String,
    val outcome: String,
)
