package com.nuguyo.app.ui.directory

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.nuguyo.app.appContainer
import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.EmployeeSource
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer

/** 어떤 삭제를 물어보는 중인지. null 이면 확인창이 없다. */
private enum class DeleteRequest { SELECTED, EVERYTHING }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectoryScreen(
    onAddEmployee: () -> Unit,
    onOpenEmployee: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: DirectoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DirectoryViewModel(container.employees) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<DeleteRequest?>(null) }

    // 선택 중에 뒤로 가기를 누르면 앱을 나가는 대신 선택을 푼다.
    BackHandler(enabled = state.selectionMode) { viewModel.clearSelection() }

    Scaffold(
        topBar = {
            if (state.selectionMode) {
                SelectionTopBar(
                    selectedCount = state.selectedIds.size,
                    allSelected = state.allVisibleSelected,
                    onClose = viewModel::clearSelection,
                    onToggleSelectAll = viewModel::toggleSelectAllVisible,
                    onDelete = { pendingDelete = DeleteRequest.SELECTED },
                )
            } else {
                DirectoryTopBar(
                    canDeleteAll = state.totalCount > 0,
                    onOpenPermissions = onOpenPermissions,
                    onOpenHistory = onOpenHistory,
                    onOpenSettings = onOpenSettings,
                    onDeleteEverything = { pendingDelete = DeleteRequest.EVERYTHING },
                )
            }
        },
        floatingActionButton = {
            // 선택 중에는 추가 버튼이 삭제 버튼과 헷갈린다.
            if (!state.selectionMode) {
                FloatingActionButton(onClick = onAddEmployee) {
                    Icon(Icons.Filled.Add, contentDescription = "직원 추가")
                }
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text("이름 · 부서 · 번호 검색") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )

            when {
                !state.loaded -> Unit

                state.totalCount == 0 -> EmptyDirectory()

                state.employees.isEmpty() -> Box(
                    Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "검색 결과가 없습니다",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // 길게 누르기는 표준 동작이지만 알아채기 어렵다. 한 줄로 알려 준다.
                    if (!state.selectionMode) {
                        item {
                            Text(
                                text = "길게 눌러 여러 명을 선택할 수 있습니다",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }
                    }
                    items(state.employees, key = { it.id }) { employee ->
                        EmployeeRow(
                            employee = employee,
                            selected = employee.id in state.selectedIds,
                            selectionMode = state.selectionMode,
                            onClick = {
                                if (state.selectionMode) {
                                    viewModel.toggleSelection(employee.id)
                                } else {
                                    onOpenEmployee(employee.id)
                                }
                            },
                            onLongClick = { viewModel.toggleSelection(employee.id) },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { request ->
        DeleteConfirmDialog(
            request = request,
            selectedCount = state.selectedIds.size,
            totalCount = state.totalCount,
            warnAboutSheet = when (request) {
                DeleteRequest.SELECTED -> state.selectionHasSheetEmployees
                DeleteRequest.EVERYTHING -> container.settings.sheetUrl != null
            },
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                when (request) {
                    DeleteRequest.SELECTED -> viewModel.deleteSelected()
                    DeleteRequest.EVERYTHING -> viewModel.deleteEverything()
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DirectoryTopBar(
    canDeleteAll: Boolean,
    onOpenPermissions: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onDeleteEverything: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    TopAppBar(
        title = { Text("직원 명부") },
        actions = {
            IconButton(onClick = onOpenPermissions) {
                Icon(Icons.Filled.Shield, contentDescription = "동작 준비")
            }
            IconButton(onClick = onOpenHistory) {
                Icon(Icons.Filled.History, contentDescription = "수신 이력")
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "설정")
            }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "더보기")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("명부 전체 삭제") },
                        enabled = canDeleteAll,
                        leadingIcon = {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
                        },
                        onClick = {
                            menuOpen = false
                            onDeleteEverything()
                        },
                    )
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    allSelected: Boolean,
    onClose: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onDelete: () -> Unit,
) {
    TopAppBar(
        // 선택 중임을 색으로도 알 수 있게 한다.
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        title = { Text("${selectedCount}명 선택") },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "선택 해제")
            }
        },
        actions = {
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector = if (allSelected) Icons.Filled.Check else Icons.Filled.SelectAll,
                    contentDescription = if (allSelected) "전체 선택 해제" else "전체 선택",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "선택 삭제")
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    request: DeleteRequest,
    selectedCount: Int,
    totalCount: Int,
    warnAboutSheet: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val count = if (request == DeleteRequest.SELECTED) selectedCount else totalCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (request == DeleteRequest.SELECTED) {
                    "${count}명을 삭제할까요?"
                } else {
                    "명부를 전부 비울까요?"
                },
            )
        },
        text = {
            Column {
                Text("직원 ${count}명의 정보와 등록된 번호가 지워집니다. 되돌릴 수 없습니다.")
                if (warnAboutSheet) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "구글 시트에서 가져온 직원은 다음 동기화 때 다시 들어옵니다. " +
                            "완전히 빼려면 시트에서도 지워야 합니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
    )
}

@Composable
private fun EmptyDirectory() {
    Box(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("등록된 직원이 없습니다", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "오른쪽 아래 + 버튼으로 직원을 추가하면,\n그 사람에게 전화나 문자가 올 때 팝업으로 알려 줍니다.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EmployeeRow(
    employee: Employee,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
                Spacer(Modifier.width(4.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                val photo = employee.photoUri
                if (photo != null) {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        text = employee.initial,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = employee.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (employee.source == EmployeeSource.SHEET) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "시트",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                val subtitle = employee.subtitle.takeIf { it.isNotBlank() }
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val primaryNumber = employee.numbers.firstOrNull()?.raw
                if (primaryNumber != null) {
                    Text(
                        text = PhoneNumberNormalizer.format(primaryNumber) +
                            if (employee.numbers.size > 1) " 외 ${employee.numbers.size - 1}건" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
