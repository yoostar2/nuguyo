package com.nuguyo.app.ui.permissions

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * 이 앱이 동작하기 위해 필요한 권한/설정 한 벌.
 *
 * 하나라도 빠지면 "전화가 와도 아무 일도 일어나지 않는" 증상으로 나타나므로,
 * 온보딩 화면에서 상태를 그대로 보여 주고 각 항목의 설정 화면으로 바로 보낸다.
 */
enum class PermissionItem(
    val title: String,
    val why: String,
    val required: Boolean,
) {
    OVERLAY(
        title = "다른 앱 위에 표시",
        why = "통화 화면 위에 팝업을 그리기 위해 반드시 필요합니다.",
        required = true,
    ),
    CALL_SCREENING_ROLE(
        title = "발신자 표시 및 스팸 앱",
        why = "걸려온 번호를 알아내는 유일한 정식 경로입니다. " +
            "기기당 한 앱만 가질 수 있어 삼성 스팸차단·후스콜 등과 함께 쓸 수 없습니다.",
        required = true,
    ),
    PHONE_STATE(
        title = "전화 상태 읽기",
        why = "전화를 받거나 끊을 때 팝업을 자동으로 닫기 위해 필요합니다.",
        required = false,
    ),
    RECEIVE_SMS(
        title = "문자 수신",
        why = "문자가 왔을 때 발신자를 알려 주기 위해 필요합니다. 문자 팝업을 쓰지 않으면 건너뛰어도 됩니다.",
        required = false,
    ),
    NOTIFICATIONS(
        title = "알림",
        why = "팝업을 띄울 수 없는 상황에서 알림으로 대신 알려 주기 위해 필요합니다.",
        required = false,
    ),
    BATTERY(
        title = "배터리 사용량 제한 없음",
        why = "삼성 절전 정책이 앱을 재우면 전화가 와도 팝업이 뜨지 않습니다. " +
            "설정 > 배터리 > 앱별 사용량에서 '제한 없음'으로 두세요.",
        required = false,
    ),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        OVERLAY -> Settings.canDrawOverlays(context)
        CALL_SCREENING_ROLE -> context.holdsCallScreeningRole()
        PHONE_STATE -> context.hasPermission(Manifest.permission.READ_PHONE_STATE)
        RECEIVE_SMS -> context.hasPermission(Manifest.permission.RECEIVE_SMS)
        NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.hasPermission(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
        BATTERY -> context.isIgnoringBatteryOptimizations()
    }

    /** 런타임 권한 요청으로 처리되는 항목이면 권한 이름, 설정 화면으로 보내야 하면 null. */
    val runtimePermission: String?
        get() = when (this) {
            PHONE_STATE -> Manifest.permission.READ_PHONE_STATE
            RECEIVE_SMS -> Manifest.permission.RECEIVE_SMS
            NOTIFICATIONS ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.POST_NOTIFICATIONS
                } else {
                    null
                }
            else -> null
        }

    /** 설정 화면으로 보내야 하는 항목의 인텐트. */
    fun settingsIntent(context: Context): Intent? = when (this) {
        OVERLAY -> Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        CALL_SCREENING_ROLE -> context.callScreeningRoleIntent()
        BATTERY -> Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        )
        else -> null
    }
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun Context.holdsCallScreeningRole(): Boolean {
    val roleManager = getSystemService(RoleManager::class.java) ?: return false
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return false
    return roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
}

/**
 * 역할 요청 다이얼로그. 다른 앱이 역할을 쥐고 있으면 시스템이 "바꾸겠습니까"를 묻는다.
 * 기기가 이 역할 자체를 지원하지 않으면 null.
 */
fun Context.callScreeningRoleIntent(): Intent? {
    val roleManager = getSystemService(RoleManager::class.java) ?: return null
    if (!roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) return null
    return roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
}

fun Context.isIgnoringBatteryOptimizations(): Boolean {
    val power = getSystemService(PowerManager::class.java) ?: return false
    return power.isIgnoringBatteryOptimizations(packageName)
}

/** 앱 정보 화면. 권한 자동 회수(App Hibernation) 해제 안내용. */
fun Context.appDetailsIntent(): Intent =
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:$packageName"),
    )
