package com.nuguyo.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nuguyo.app.appContainer
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenSheetSync: () -> Unit) {
    val settings = LocalContext.current.appContainer.settings

    // SharedPreferences 는 흐름을 내보내지 않으므로 화면 진입 시 한 번 읽어 온다.
    var callEnabled by remember { mutableStateOf(settings.callPopupEnabled) }
    var smsEnabled by remember { mutableStateOf(settings.smsPopupEnabled) }
    var showUnknown by remember { mutableStateOf(settings.showUnknownNumbers) }
    var dismissOnAnswer by remember { mutableStateOf(settings.dismissOnAnswer) }
    val smsSeconds = remember { mutableFloatStateOf(settings.smsPopupSeconds.toFloat()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("설정") },
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
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenSheetSync),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("구글 시트 연동", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = settings.sheetUrl?.let { "연동됨 · " + (settings.lastSync?.summary ?: "아직 가져오지 않음") }
                                    ?: "명부를 구글 시트에서 가져옵니다",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    }
                }
            }
            item {
                SettingSwitch(
                    title = "전화 팝업",
                    description = "등록된 직원에게서 전화가 오면 팝업을 띄웁니다.",
                    checked = callEnabled,
                    onChange = {
                        callEnabled = it
                        settings.callPopupEnabled = it
                    },
                )
            }
            item {
                SettingSwitch(
                    title = "문자 팝업",
                    description = "문자를 받으면 발신자와 내용 미리보기를 띄웁니다.",
                    checked = smsEnabled,
                    onChange = {
                        smsEnabled = it
                        settings.smsPopupEnabled = it
                    },
                )
            }
            item {
                SettingSwitch(
                    title = "미등록 번호도 알리기",
                    description = "명부에 없는 번호에도 팝업을 띄웁니다. 스팸까지 뜰 수 있습니다.",
                    checked = showUnknown,
                    onChange = {
                        showUnknown = it
                        settings.showUnknownNumbers = it
                    },
                )
            }
            item {
                SettingSwitch(
                    title = "전화를 받으면 팝업 닫기",
                    description = "끄면 통화 중에도 메모를 계속 볼 수 있습니다.",
                    checked = dismissOnAnswer,
                    onChange = {
                        dismissOnAnswer = it
                        settings.dismissOnAnswer = it
                    },
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("문자 팝업 유지 시간", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "${smsSeconds.floatValue.roundToInt()}초 후 자동으로 사라집니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = smsSeconds.floatValue,
                            onValueChange = { smsSeconds.floatValue = it },
                            onValueChangeFinished = {
                                settings.smsPopupSeconds = smsSeconds.floatValue.roundToInt()
                            },
                            valueRange = 3f..60f,
                        )
                    }
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("직원 정보 보관", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "명부는 이 기기 안에만 저장되며 어디로도 전송되지 않습니다. " +
                                "클라우드 백업에서도 제외되어 있습니다.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}
