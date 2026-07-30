package com.nuguyo.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.nuguyo.app.R
import com.nuguyo.app.ui.MainActivity

object Notifications {

    /** 팝업이 떠 있는 동안만 유지되는 조용한 알림. 사용자 눈에 거의 띄지 않아야 한다. */
    const val CHANNEL_OVERLAY = "overlay"

    /** 오버레이를 못 띄울 때 대신 쓰는 폴백 채널. */
    const val CHANNEL_ALERT = "caller_alert"

    const val ID_OVERLAY_FGS = 1001
    const val ID_FALLBACK = 1002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_OVERLAY,
                context.getString(R.string.channel_overlay_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.channel_overlay_desc)
                setShowBadge(false)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert_name),
                // 오버레이 실패 시의 최후 통보 수단이므로 헤드업으로 떠야 한다.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.channel_alert_desc)
            },
        )
    }

    /** 포그라운드 서비스 요건을 채우기 위한 최소 알림. */
    fun foregroundNotification(context: Context): Notification =
        Notification.Builder(context, CHANNEL_OVERLAY)
            .setSmallIcon(R.drawable.ic_stat_nuguyo)
            .setContentTitle(context.getString(R.string.fgs_notification_title))
            .setOngoing(true)
            .build()

    /**
     * 오버레이 권한이 없거나 창을 붙이는 데 실패했을 때의 폴백.
     * 팝업만큼 눈에 띄지는 않지만 "누구인지"는 전달된다.
     */
    fun postCallerFallback(context: Context, title: String, body: String?) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = Notification.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_stat_nuguyo)
            .setContentTitle(title)
            .apply { body?.let { setContentText(it) } }
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_CALL)
            .build()
        context.getSystemService(NotificationManager::class.java)
            ?.notify(ID_FALLBACK, notification)
    }

    fun cancelFallback(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(ID_FALLBACK)
    }
}
