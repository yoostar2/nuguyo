package com.nuguyo.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer

/** 명부에서 바로 실행할 수 있는 동작. */
enum class PhoneAction { CALL, MESSAGE }

/**
 * 기본 전화앱/문자앱을 번호가 채워진 상태로 연다.
 *
 * 전화는 `ACTION_CALL` 이 아니라 `ACTION_DIAL` 을 쓴다. 다이얼러에 번호만 넣어 주고
 * 거는 것은 사용자가 결정하게 하면 `CALL_PHONE` 권한이 아예 필요 없고, 목록에서
 * 잘못 눌러 전화가 걸리는 사고도 없다.
 */
object PhoneActions {

    fun launch(context: Context, action: PhoneAction, rawNumber: String) {
        val number = dialableNumber(rawNumber)
        if (number.isEmpty()) {
            toast(context, "걸 수 있는 번호가 아닙니다")
            return
        }

        // fromParts 로 만들면 # * 같은 문자가 스킴 뒤에서 알아서 인코딩된다.
        val uri = when (action) {
            PhoneAction.CALL -> Uri.fromParts("tel", number, null)
            PhoneAction.MESSAGE -> Uri.fromParts("smsto", number, null)
        }
        val intent = Intent(
            if (action == PhoneAction.CALL) Intent.ACTION_DIAL else Intent.ACTION_SENDTO,
            uri,
        )

        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "처리할 앱을 찾지 못했다: $action", e)
            toast(
                context,
                when (action) {
                    PhoneAction.CALL -> "전화 앱을 찾을 수 없습니다"
                    PhoneAction.MESSAGE -> "문자 앱을 찾을 수 없습니다"
                },
            )
        }
    }

    /**
     * 다이얼러에는 E.164 를 넘긴다. 사용자가 `010 1234 5678` 처럼 띄어쓰기를 섞어
     * 입력해 두었어도 확실히 걸리게 하기 위해서다. 정규화가 안 되는 사내 내선
     * 같은 번호는 숫자만 남겨 그대로 넘긴다.
     */
    private fun dialableNumber(raw: String): String {
        val normalized = PhoneNumberNormalizer.normalize(raw)
        return normalized.e164 ?: normalized.digits
    }

    private fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private const val TAG = "PhoneActions"
}
