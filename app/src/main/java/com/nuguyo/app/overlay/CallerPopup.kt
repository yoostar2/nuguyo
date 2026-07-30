package com.nuguyo.app.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Message
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nuguyo.app.domain.model.ContactKind
import com.nuguyo.app.domain.model.ContactSummary
import com.nuguyo.app.domain.model.Employee
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** 팝업 한 번에 필요한 모든 것. 서비스가 조립해서 넘긴다. */
data class CallerPopupState(
    val kind: ContactKind,
    val number: String,
    val employee: Employee?,
    val summary: ContactSummary = ContactSummary(),
    val preview: String? = null,
    /** 뒤 8자리만 일치해서 찾은 경우. 확신이 덜하다는 걸 화면에 밝힌다. */
    val isLooseMatch: Boolean = false,
)

@Composable
fun CallerPopup(
    state: CallerPopupState,
    onDismiss: () -> Unit,
    onOpenDetail: () -> Unit,
    onDragBy: (Int) -> Unit,
) {
    val employee = state.employee

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            // 통화 화면의 버튼을 가리면 사용자가 직접 치워야 한다.
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount ->
                    onDragBy(dragAmount.roundToInt())
                }
            },
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(employee)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = employee?.name ?: "등록되지 않은 번호",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val subtitle = employee?.subtitle?.takeIf { it.isNotBlank() }
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (state.kind == ContactKind.CALL) {
                                Icons.Filled.Phone
                            } else {
                                Icons.Filled.Message
                            },
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = state.number,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "닫기")
                }
            }

            if (state.isLooseMatch) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "번호 뒷자리만 일치합니다. 동명이인/내선 여부를 확인하세요.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (employee != null && employee.employeeNo != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "사번 ${employee.employeeNo}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (employee != null && employee.tags.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    employee.tags.take(3).forEach { tag ->
                        AssistChip(
                            onClick = {},
                            label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                            colors = AssistChipDefaults.assistChipColors(),
                        )
                    }
                }
            }

            employee?.memo?.takeIf { it.isNotBlank() }?.let { memo ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = memo,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            state.preview?.takeIf { it.isNotBlank() }?.let { preview ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = preview,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val summaryText = summaryLine(state.summary)
            if (summaryText != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summaryText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onOpenDetail) {
                    Text(if (employee != null) "상세 보기" else "직원으로 등록")
                }
            }
        }
    }
}

@Composable
private fun Avatar(employee: Employee?) {
    val photo = employee?.photoUri
    Box(
        modifier = Modifier
            .size(52.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = null,
                modifier = Modifier.size(52.dp),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = employee?.initial ?: "?",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

private fun summaryLine(summary: ContactSummary): String? {
    if (summary.totalCount <= 0) return null
    val last = summary.lastContactAt ?: return "이번이 첫 연락입니다"
    val days = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - last)
    val ago = when {
        days <= 0L -> "오늘"
        days == 1L -> "어제"
        days < 30L -> "${days}일 전"
        else -> "${days / 30}개월 전"
    }
    return "지금까지 ${summary.totalCount}회 · 직전 연락 $ago"
}
