package com.nuguyo.app.ui.permissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuguyo.app.appContainer
import com.nuguyo.app.data.ScreeningTrace
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.service.CallerOverlayService
import kotlinx.coroutines.delay

/** 설정 앱을 다녀오거나 전화를 걸어 본 뒤 돌아왔을 때 값이 갱신되어 있도록. */
private const val REFRESH_INTERVAL_MS = 2_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { PermissionItem.entries.toList() }

    // 이 화면은 사용자가 설정 앱을 오가고 실제로 전화도 걸어 보는 곳이라, 돌아왔을 때
    // 값이 갱신되어 있어야 한다. 콜백만으로는 놓치는 경로가 있어 주기적으로 다시 읽는다.
    val refreshKey = remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(REFRESH_INTERVAL_MS)
            refreshKey.intValue++
        }
    }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey.intValue++ }
    val openSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshKey.intValue++ }

    val statuses = remember(refreshKey.intValue) { items.associateWith { it.isGranted(context) } }
    val missingRequired = items.count { it.required && statuses[it] == false }
    val screening = remember(refreshKey.intValue) { context.appContainer.settings.lastScreening }
    val roleAvailable = remember(refreshKey.intValue) { context.callScreeningRoleIntent() != null }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("동작 준비") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = if (missingRequired == 0) {
                                "필수 항목이 모두 준비되었습니다"
                            } else {
                                "필수 항목 ${missingRequired}개가 남았습니다"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "아래 항목이 채워지지 않으면 전화가 와도 팝업이 뜨지 않습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            items(items) { permissionItem ->
                PermissionRow(
                    item = permissionItem,
                    granted = statuses[permissionItem] == true,
                    unsupported = permissionItem == PermissionItem.CALL_SCREENING_ROLE && !roleAvailable,
                    onFix = {
                        val runtime = permissionItem.runtimePermission
                        if (runtime != null) {
                            requestPermission.launch(runtime)
                        } else {
                            permissionItem.settingsIntent(context)?.let(openSettings::launch)
                        }
                    },
                )
            }

            item {
                ScreeningDiagnosticsCard(
                    trace = screening,
                    roleHeld = statuses[PermissionItem.CALL_SCREENING_ROLE] == true,
                )
            }

            item { TestPopupCard() }

            item {
                OutlinedButton(
                    onClick = { openSettings.launch(context.appDetailsIntent()) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("앱 정보 열기 (권한 자동 회수 해제)")
                }
            }
        }
    }
}

/**
 * 전화가 와도 팝업이 안 뜰 때 원인을 좁히기 위한 카드.
 *
 * 역할을 못 받아 콜백 자체가 안 온 것인지, 콜백은 왔는데 명부에서 못 찾은 것인지를
 * 사용자가 로그캣 없이 구분할 수 있어야 한다.
 */
@Composable
private fun ScreeningDiagnosticsCard(trace: ScreeningTrace?, roleHeld: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("통화 감지 진단", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))

            if (trace == null) {
                Text(
                    text = "아직 감지된 통화가 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = if (roleHeld) {
                        "다른 전화기로 이 폰에 전화를 걸어 본 뒤 이 화면으로 돌아오세요. " +
                            "그래도 비어 있으면 통화 감지 콜백 자체가 오지 않는 것입니다."
                    } else {
                        "'발신자 표시 및 스팸 앱' 역할이 이 앱에 없습니다. " +
                            "위에서 먼저 허용해 주세요. 이 역할이 없으면 걸려온 번호를 알 수 없습니다."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }

            Text(
                text = "마지막 감지: ${relativeTime(trace.at)}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "번호: ${trace.number}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "결과: ${trace.outcome}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun relativeTime(at: Long): String {
    val elapsed = System.currentTimeMillis() - at
    val minutes = elapsed / 60_000
    return when {
        minutes < 1 -> "방금 전"
        minutes < 60 -> "${minutes}분 전"
        minutes < 60 * 24 -> "${minutes / 60}시간 전"
        else -> "${minutes / (60 * 24)}일 전"
    }
}

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
    unsupported: Boolean,
    onFix: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = if (granted) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Filled.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (granted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall)
                    if (item.required) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "필수",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = item.why,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    if (unsupported) {
                        // 버튼을 눌러도 열 화면이 없는 상태다. 아무 반응 없이 두면
                        // 사용자는 허용했다고 착각한다.
                        Text(
                            text = "이 기기에서는 이 역할을 제공하지 않습니다. " +
                                "설정 > 앱 > 기본 앱 선택에서 직접 지정해야 할 수 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Button(onClick = onFix) { Text("허용하기") }
                    }
                }
            }
        }
    }
}

/** 실제 전화를 걸어 보지 않고도 팝업 모양과 오버레이 권한을 확인할 수 있게 한다. */
@Composable
private fun TestPopupCard() {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("팝업 미리 보기", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                text = "전화를 걸어 보지 않고 팝업이 정상적으로 뜨는지 확인합니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    CallerOverlayService.show(
                        context = context,
                        employeeId = null,
                        number = "010-0000-0000",
                        kind = ContactKind.SMS,
                        preview = "테스트 팝업입니다. 잠시 후 사라집니다.",
                    )
                },
            ) {
                Text("테스트 팝업 띄우기")
            }
        }
    }
}
