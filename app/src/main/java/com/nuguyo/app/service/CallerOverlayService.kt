package com.nuguyo.app.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.nuguyo.app.appContainer
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.domain.model.ContactSummary
import com.nuguyo.app.overlay.CallerPopup
import com.nuguyo.app.overlay.CallerPopupState
import com.nuguyo.app.overlay.OverlayHost
import com.nuguyo.app.ui.MainActivity
import com.nuguyo.app.ui.theme.NuguyoTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

/**
 * 팝업 창의 수명을 붙잡아 두는 포그라운드 서비스.
 *
 * `CallScreeningService` 는 응답 직후 언바인드될 수 있어서 창을 오래 들고 있을 수 없다.
 * FGS 타입이 `phoneCall` 이 아니라 `specialUse` 인 건 `phoneCall` 이 기본 전화앱 또는
 * `MANAGE_OWN_CALLS` 보유 앱에만 허용되기 때문이다.
 */
class CallerOverlayService : Service() {

    private lateinit var host: OverlayHost
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var unregisterCallState: (() -> Unit)? = null
    private var dismissJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        host = OverlayHost(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // FGS 는 시작 직후 알림을 올려야 한다. 어떤 분기로 빠지든 먼저 처리한다.
        promoteToForeground()

        when (intent?.action) {
            ACTION_SHOW -> handleShow(intent)
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun promoteToForeground() {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_OVERLAY_FGS,
                Notifications.foregroundNotification(this),
                type,
            )
        }.onFailure { Log.w(TAG, "포그라운드 승격 실패", it) }
    }

    private fun handleShow(intent: Intent) {
        val employeeId = intent.getStringExtra(EXTRA_EMPLOYEE_ID)
        val number = intent.getStringExtra(EXTRA_NUMBER).orEmpty()
        val kind = runCatching {
            ContactKind.valueOf(intent.getStringExtra(EXTRA_KIND).orEmpty())
        }.getOrDefault(ContactKind.CALL)
        val preview = intent.getStringExtra(EXTRA_PREVIEW)
        val looseMatch = intent.getBooleanExtra(EXTRA_LOOSE_MATCH, false)

        // 새 통화가 들어와 내용이 갈리는 경우가 있으니 기존 타이머는 먼저 끊는다.
        dismissJob?.cancel()

        scope.launch {
            val container = appContainer
            val employee = employeeId?.let {
                withContext(Dispatchers.IO) { runCatching { container.employees.find(it) }.getOrNull() }
            }
            val summary = employeeId?.let {
                withContext(Dispatchers.IO) {
                    runCatching {
                        // 방금 기록한 이번 연락은 "직전 연락"에서 빼야 한다.
                        container.events.summaryFor(it, System.currentTimeMillis() - RECENT_GRACE_MS)
                    }.getOrNull()
                }
            } ?: ContactSummary()

            val state = CallerPopupState(
                kind = kind,
                number = number,
                employee = employee,
                summary = summary,
                preview = preview,
                isLooseMatch = looseMatch,
            )

            val shown = host.show(container.settings.popupOffsetY) {
                NuguyoTheme {
                    CallerPopup(
                        state = state,
                        onDismiss = { stopSelf() },
                        onOpenDetail = { openInApp(employee?.id, number) },
                        onDragBy = { host.moveBy(it) },
                    )
                }
            }

            if (!shown) {
                Notifications.postCallerFallback(
                    context = this@CallerOverlayService,
                    title = employee?.name ?: number,
                    body = listOfNotNull(employee?.subtitle?.takeIf { it.isNotBlank() }, preview, number)
                        .firstOrNull(),
                )
                stopSelf()
                return@launch
            }

            armDismissal(kind)
        }
    }

    /** 팝업이 화면에 남는 조건을 정한다. */
    private fun armDismissal(kind: ContactKind) {
        val settings = appContainer.settings
        if (kind == ContactKind.CALL) {
            watchCallState { state ->
                when (state) {
                    TelephonyManager.CALL_STATE_IDLE -> stopSelf()
                    TelephonyManager.CALL_STATE_OFFHOOK ->
                        if (settings.dismissOnAnswer) stopSelf()
                    else -> Unit
                }
            }
            // 통화 상태를 못 받는 상황(권한 미허용 등)에서도 창이 영구히 남지 않게.
            dismissJob = scope.launch {
                delay(CALL_WATCHDOG_MS)
                stopSelf()
            }
        } else {
            dismissJob = scope.launch {
                delay(settings.smsPopupSeconds * 1000L)
                stopSelf()
            }
        }
    }

    /**
     * 통화 상태 구독. `TelephonyCallback` 은 API 31 부터라서 그 아래에서는
     * 사용 중단된 `PhoneStateListener` 를 쓴다.
     */
    private fun watchCallState(onState: (Int) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.i(TAG, "READ_PHONE_STATE 가 없어 워치독 시간에만 의존한다")
            return
        }
        val manager = getSystemService(TelephonyManager::class.java) ?: return
        unregisterCallState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ModernCallState.register(manager, mainExecutor, onState)
        } else {
            LegacyCallState.register(manager, onState)
        }
    }

    private fun openInApp(employeeId: String?, number: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            if (employeeId != null) {
                putExtra(MainActivity.EXTRA_EMPLOYEE_ID, employeeId)
            } else {
                putExtra(MainActivity.EXTRA_PREFILL_NUMBER, number)
            }
        }
        // SYSTEM_ALERT_WINDOW 를 가진 앱은 백그라운드 액티비티 실행 제한에서 면제된다.
        runCatching { startActivity(intent) }
            .onFailure { Log.w(TAG, "앱 화면을 열지 못했다", it) }
        stopSelf()
    }

    override fun onDestroy() {
        // 사용자가 옮겨 놓은 위치를 다음 통화에서도 유지한다.
        runCatching { appContainer.settings.popupOffsetY = host.offsetY }
        unregisterCallState?.invoke()
        unregisterCallState = null
        host.dismiss()
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private object ModernCallState {
        fun register(
            manager: TelephonyManager,
            executor: Executor,
            onState: (Int) -> Unit,
        ): () -> Unit {
            val callback = object : android.telephony.TelephonyCallback(),
                android.telephony.TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) = onState(state)
            }
            manager.registerTelephonyCallback(executor, callback)
            return { runCatching { manager.unregisterTelephonyCallback(callback) } }
        }
    }

    @Suppress("DEPRECATION")
    private object LegacyCallState {
        fun register(manager: TelephonyManager, onState: (Int) -> Unit): () -> Unit {
            val listener = object : android.telephony.PhoneStateListener() {
                override fun onCallStateChanged(state: Int, phoneNumber: String?) = onState(state)
            }
            manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_CALL_STATE)
            return {
                runCatching {
                    manager.listen(listener, android.telephony.PhoneStateListener.LISTEN_NONE)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CallerOverlay"

        private const val ACTION_SHOW = "com.nuguyo.app.action.SHOW_CALLER"
        private const val EXTRA_EMPLOYEE_ID = "employee_id"
        private const val EXTRA_NUMBER = "number"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PREVIEW = "preview"
        private const val EXTRA_LOOSE_MATCH = "loose_match"

        /** 통화 상태 신호를 놓쳤을 때 창을 강제로 걷어내는 시간. */
        private const val CALL_WATCHDOG_MS = 90_000L

        /** "직전 연락" 계산에서 방금 기록한 이번 연락을 빼기 위한 여유. */
        private const val RECENT_GRACE_MS = 5_000L

        /**
         * 팝업을 요청한다.
         * @return 서비스를 시작하지 못하면 false. 호출부는 알림 폴백으로 넘어가야 한다.
         */
        fun show(
            context: Context,
            employeeId: String?,
            number: String,
            kind: ContactKind,
            preview: String? = null,
            looseMatch: Boolean = false,
        ): Boolean {
            val intent = Intent(context, CallerOverlayService::class.java).apply {
                action = ACTION_SHOW
                putExtra(EXTRA_EMPLOYEE_ID, employeeId)
                putExtra(EXTRA_NUMBER, number)
                putExtra(EXTRA_KIND, kind.name)
                putExtra(EXTRA_PREVIEW, preview)
                putExtra(EXTRA_LOOSE_MATCH, looseMatch)
            }
            return try {
                context.startForegroundService(intent)
                true
            } catch (t: Throwable) {
                // API 31+ 의 ForegroundServiceStartNotAllowedException 등.
                Log.w(TAG, "포그라운드 서비스를 시작할 수 없다", t)
                false
            }
        }
    }
}
