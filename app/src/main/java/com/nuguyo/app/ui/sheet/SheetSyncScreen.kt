package com.nuguyo.app.ui.sheet

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.nuguyo.app.appContainer
import com.nuguyo.app.data.SyncTrace
import com.nuguyo.app.data.sheet.GoogleSheetUrl
import com.nuguyo.app.data.sheet.SheetRowIssue
import com.nuguyo.app.data.sheet.SyncResult
import com.nuguyo.app.service.SheetSyncWorker
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetSyncScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val container = context.appContainer
    val settings = container.settings
    val scope = rememberCoroutineScope()

    var url by remember { mutableStateOf(settings.sheetUrl.orEmpty()) }
    var autoSync by remember { mutableStateOf(settings.sheetAutoSync) }
    var syncing by remember { mutableStateOf(false) }
    var trace by remember { mutableStateOf(settings.lastSync) }
    var issues by remember { mutableStateOf(emptyList<SheetRowIssue>()) }

    val csvUrl = remember(url) { GoogleSheetUrl.toCsvUrl(url) }
    val urlLooksValid = url.isBlank() || csvUrl != null

    fun runSync() {
        settings.sheetUrl = url
        SheetSyncWorker.apply(context)
        syncing = true
        scope.launch {
            val result = container.sheetSync.sync()
            issues = (result as? SyncResult.Success)?.issues.orEmpty()
            trace = settings.lastSync
            syncing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("구글 시트 연동") },
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
                        Text("시트 주소", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            label = { Text("구글 시트 링크 붙여넣기") },
                            isError = !urlLooksValid,
                            supportingText = {
                                Text(
                                    when {
                                        !urlLooksValid -> "구글 시트 주소로 보이지 않습니다"
                                        url.isBlank() -> "주소창의 링크나 공유 링크를 그대로 붙여넣으면 됩니다"
                                        else -> "확인됨"
                                    },
                                )
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = ::runSync,
                                enabled = !syncing && csvUrl != null,
                            ) {
                                if (syncing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Text(if (syncing) "가져오는 중" else "지금 가져오기")
                            }
                            Spacer(Modifier.width(8.dp))
                            if (settings.sheetUrl != null) {
                                OutlinedButton(
                                    enabled = !syncing,
                                    onClick = {
                                        url = ""
                                        settings.sheetUrl = null
                                        SheetSyncWorker.apply(context)
                                        trace = null
                                        issues = emptyList()
                                    },
                                ) {
                                    Text("연동 해제")
                                }
                            }
                        }
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("자동으로 가져오기", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "12시간마다 시트를 다시 읽습니다. 꺼도 위 버튼으로 언제든 가져올 수 있습니다.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = autoSync,
                            onCheckedChange = {
                                autoSync = it
                                settings.sheetAutoSync = it
                                SheetSyncWorker.apply(context)
                            },
                        )
                    }
                }
            }

            item { LastSyncCard(trace, issues) }
            item { SheetFormatCard() }
            item { PrivacyCard() }
        }
    }
}

@Composable
private fun LastSyncCard(trace: SyncTrace?, issues: List<SheetRowIssue>) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("마지막 가져오기", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            if (trace == null) {
                Text(
                    text = "아직 가져온 적이 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            Text(
                text = "${TIME_FORMAT.format(Date(trace.at))} · ${trace.summary}",
                style = MaterialTheme.typography.bodyMedium,
                color = if (trace.success) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.error
                },
            )
            if (issues.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("건너뛴 줄", style = MaterialTheme.typography.labelMedium)
                issues.take(MAX_ISSUES).forEach { issue ->
                    Text(
                        text = "${issue.rowNumber}행 — ${issue.reason}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (issues.size > MAX_ISSUES) {
                    Text(
                        text = "외 ${issues.size - MAX_ISSUES}줄",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetFormatCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("시트 양식", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "첫 줄을 제목 줄로 두고 아래처럼 적으면 됩니다. " +
                    "열 순서는 상관없고, 필요 없는 열은 빼도 됩니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "이름 | 부서 | 직급 | 사번 | 전화번호 | 태그 | 메모\n" +
                    "김서연 | 영업2팀 | 과장 | 20180412 | 010-2967-1626 | 주요고객 | 세종점 담당",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "• 이름과 전화번호는 반드시 있어야 합니다\n" +
                    "• 번호가 여러 개면 한 칸에 쉼표나 줄바꿈으로 나누거나, " +
                    "전화번호2 처럼 열을 더 만들어도 됩니다\n" +
                    "• 사번을 적어 두면 시트에서 이름이나 번호를 고쳐도 같은 사람으로 이어집니다",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("공유 설정과 보안", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "시트에서 공유 > '링크가 있는 모든 사용자'를 뷰어로 설정해야 앱이 읽을 수 있습니다.\n\n" +
                    "그 대신 링크를 아는 사람은 누구나 시트를 볼 수 있게 됩니다. " +
                    "직원 이름과 전화번호가 들어가므로 링크를 외부에 공유하지 마세요.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val MAX_ISSUES = 5
private val TIME_FORMAT = SimpleDateFormat("M/d HH:mm", Locale.KOREA)
