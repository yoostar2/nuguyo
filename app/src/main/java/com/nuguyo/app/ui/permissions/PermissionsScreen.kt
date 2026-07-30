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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.service.CallerOverlayService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val items = remember { PermissionItem.entries.toList() }

    // 설정 화면은 결과를 돌려주지 않지만, StartActivityForResult 콜백은 사용자가
    // 뒤로 나올 때도 호출된다. 그 시점에 상태를 다시 읽는다.
    val refreshKey = remember { mutableIntStateOf(0) }
    val requestPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshKey.intValue++ }
    val openSettings = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { refreshKey.intValue++ }

    val statuses = remember(refreshKey.intValue) { items.associateWith { it.isGranted(context) } }
    val missingRequired = items.count { it.required && statuses[it] == false }

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

@Composable
private fun PermissionRow(
    item: PermissionItem,
    granted: Boolean,
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
                    Button(onClick = onFix) { Text("허용하기") }
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
