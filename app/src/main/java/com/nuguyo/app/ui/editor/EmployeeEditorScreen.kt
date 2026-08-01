package com.nuguyo.app.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import coil.compose.AsyncImage
import com.nuguyo.app.appContainer
import com.nuguyo.app.data.copyPhotoToAppStorage
import com.nuguyo.app.data.deleteAppStoragePhoto
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmployeeEditorScreen(
    employeeId: String?,
    prefillNumber: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val container = context.appContainer
    val viewModel: EmployeeEditorViewModel = viewModel(
        factory = viewModelFactory {
            initializer { EmployeeEditorViewModel(container.employees) }
        },
    )

    LaunchedEffect(employeeId, prefillNumber) {
        viewModel.start(employeeId, prefillNumber)
    }

    val form = viewModel.form
    var confirmDelete by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val stored = copyPhotoToAppStorage(context, uri)
                if (stored != null) {
                    deleteAppStoragePhoto(viewModel.form.photoUri)
                    viewModel.onPhoto(stored.toString())
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (form.isNew) "직원 추가" else "직원 편집") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
                actions = {
                    if (!form.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "삭제")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .clickable {
                            pickPhoto.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = form.photoUri
                    if (photo != null) {
                        AsyncImage(
                            model = photo,
                            contentDescription = null,
                            modifier = Modifier.size(72.dp),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = "사진 선택")
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("사진", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "팝업에 함께 표시됩니다",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (form.photoUri != null) {
                        TextButton(
                            onClick = {
                                deleteAppStoragePhoto(form.photoUri)
                                viewModel.onPhoto(null)
                            },
                        ) {
                            Text("사진 제거")
                        }
                    }
                }
            }

            OutlinedTextField(
                value = form.name,
                onValueChange = viewModel::onName,
                label = { Text("이름 *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.department,
                    onValueChange = viewModel::onDepartment,
                    label = { Text("부서") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.title,
                    onValueChange = viewModel::onTitle,
                    label = { Text("직급") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.employeeNo,
                onValueChange = viewModel::onEmployeeNo,
                label = { Text("사번") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "전화번호",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 8.dp),
            )
            form.numbers.forEach { field ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = field.raw,
                            onValueChange = { viewModel.onNumberChange(field.key, it) },
                            label = { Text("번호") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            supportingText = {
                                val normalized = PhoneNumberNormalizer.normalize(field.raw)
                                if (field.raw.isNotBlank() && !normalized.isUsable) {
                                    Text(
                                        text = "이 번호로는 매칭할 수 없습니다",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                } else if (normalized.e164 != null) {
                                    Text(normalized.e164)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = field.label,
                            onValueChange = { viewModel.onNumberLabelChange(field.key, it) },
                            label = { Text("구분 (휴대폰/사내/직통)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    IconButton(onClick = { viewModel.removeNumber(field.key) }) {
                        Icon(Icons.Filled.Close, contentDescription = "번호 삭제")
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            TextButton(onClick = viewModel::addNumber) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("번호 추가")
            }

            OutlinedTextField(
                value = form.tagsText,
                onValueChange = viewModel::onTags,
                label = { Text("태그 (쉼표로 구분)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.memo,
                onValueChange = viewModel::onMemo,
                label = { Text("메모") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { viewModel.save(onDone) },
                enabled = form.canSave && !viewModel.loading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("저장")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("삭제할까요?") },
            text = { Text("${form.name} 님의 명부 정보와 등록된 번호가 모두 지워집니다.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete(onDone)
                }) {
                    Text("삭제")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("취소") }
            },
        )
    }
}
