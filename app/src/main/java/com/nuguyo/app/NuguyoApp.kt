package com.nuguyo.app

import android.app.Application
import android.content.Context
import com.nuguyo.app.data.EmployeeRepository
import com.nuguyo.app.data.EventRepository
import com.nuguyo.app.data.SettingsStore
import com.nuguyo.app.data.db.NuguyoDatabase
import com.nuguyo.app.domain.lookup.CallerLookup
import com.nuguyo.app.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 의존성 조립. Hilt 를 쓰지 않은 건 이 앱의 그래프가 열 개도 안 되는데
 * 시스템이 직접 인스턴스화하는 컴포넌트(CallScreeningService, BroadcastReceiver)마다
 * EntryPoint 를 붙이는 비용이 더 크기 때문이다.
 */
class AppContainer(context: Context) {
    private val database by lazy { NuguyoDatabase.build(context) }

    val employees: EmployeeRepository by lazy { EmployeeRepository(database.employeeDao()) }
    val events: EventRepository by lazy { EventRepository(database.contactEventDao()) }
    val lookup: CallerLookup by lazy { CallerLookup(employees) }
    val settings: SettingsStore by lazy { SettingsStore(context) }

    /**
     * 화면이나 서비스보다 오래 살아야 하는 곁작업용 스코프.
     * 이력 기록처럼 "놓쳐도 통화 자체엔 영향 없는" 쓰기에만 쓴다.
     */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}

class NuguyoApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)

        // 첫 통화에서 DB 열기 지연을 물지 않도록 미리 데워 두고, 묵은 이력을 정리한다.
        container.appScope.launch {
            container.lookup.warmUp()
            runCatching { container.events.pruneOlderThan() }
        }
    }
}

/** 서비스/리시버/Composable 어디서든 같은 방식으로 그래프에 닿게 해 주는 확장. */
val Context.appContainer: AppContainer
    get() = (applicationContext as NuguyoApp).container
