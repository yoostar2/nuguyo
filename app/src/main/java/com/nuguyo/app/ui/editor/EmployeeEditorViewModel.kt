package com.nuguyo.app.ui.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuguyo.app.data.EmployeeRepository
import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.StaffNumber
import kotlinx.coroutines.launch
import java.util.UUID

/** 편집 화면의 입력 상태. 저장 전까지는 DB 를 건드리지 않는다. */
data class EditorForm(
    val id: String = "",
    val name: String = "",
    val department: String = "",
    val title: String = "",
    val employeeNo: String = "",
    val memo: String = "",
    val tagsText: String = "",
    val photoUri: String? = null,
    val numbers: List<NumberField> = listOf(NumberField()),
) {
    val isNew: Boolean get() = id.isBlank()
    val canSave: Boolean
        get() = name.isNotBlank() && numbers.any { it.raw.isNotBlank() }
}

data class NumberField(
    val key: String = UUID.randomUUID().toString(),
    val id: String = "",
    val raw: String = "",
    val label: String = "",
)

class EmployeeEditorViewModel(private val employees: EmployeeRepository) : ViewModel() {

    var form by mutableStateOf(EditorForm())
        private set

    var loading by mutableStateOf(false)
        private set

    /** 기존 직원을 불러오거나(id), 팝업에서 넘어온 번호를 미리 채운다(prefillNumber). */
    fun start(employeeId: String?, prefillNumber: String?) {
        if (employeeId.isNullOrBlank()) {
            if (!prefillNumber.isNullOrBlank() && form.numbers.all { it.raw.isBlank() }) {
                form = form.copy(numbers = listOf(NumberField(raw = prefillNumber)))
            }
            return
        }
        if (form.id == employeeId) return

        loading = true
        viewModelScope.launch {
            val loaded = employees.find(employeeId)
            if (loaded != null) form = loaded.toForm()
            loading = false
        }
    }

    fun onName(value: String) { form = form.copy(name = value) }
    fun onDepartment(value: String) { form = form.copy(department = value) }
    fun onTitle(value: String) { form = form.copy(title = value) }
    fun onEmployeeNo(value: String) { form = form.copy(employeeNo = value) }
    fun onMemo(value: String) { form = form.copy(memo = value) }
    fun onTags(value: String) { form = form.copy(tagsText = value) }
    fun onPhoto(uri: String?) { form = form.copy(photoUri = uri) }

    fun onNumberChange(key: String, raw: String) {
        form = form.copy(
            numbers = form.numbers.map { if (it.key == key) it.copy(raw = raw) else it },
        )
    }

    fun onNumberLabelChange(key: String, label: String) {
        form = form.copy(
            numbers = form.numbers.map { if (it.key == key) it.copy(label = label) else it },
        )
    }

    fun addNumber() {
        form = form.copy(numbers = form.numbers + NumberField())
    }

    fun removeNumber(key: String) {
        val remaining = form.numbers.filterNot { it.key == key }
        // 입력란이 하나도 없으면 다시 채울 수 없으니 빈 칸 하나는 남긴다.
        form = form.copy(numbers = remaining.ifEmpty { listOf(NumberField()) })
    }

    fun save(onSaved: () -> Unit) {
        if (!form.canSave) return
        viewModelScope.launch {
            employees.save(form.toEmployee())
            onSaved()
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = form.id
        if (id.isBlank()) return
        viewModelScope.launch {
            employees.delete(id)
            onDeleted()
        }
    }
}

private fun Employee.toForm() = EditorForm(
    id = id,
    name = name,
    department = department.orEmpty(),
    title = title.orEmpty(),
    employeeNo = employeeNo.orEmpty(),
    memo = memo.orEmpty(),
    tagsText = tags.joinToString(", "),
    photoUri = photoUri,
    numbers = numbers
        .map { NumberField(id = it.id, raw = it.raw, label = it.label.orEmpty()) }
        .ifEmpty { listOf(NumberField()) },
)

private fun EditorForm.toEmployee() = Employee(
    id = id,
    name = name,
    department = department,
    title = title,
    employeeNo = employeeNo,
    photoUri = photoUri,
    memo = memo,
    tags = tagsText.split(',', ' ')
        .map { it.trim() }
        .filter { it.isNotEmpty() },
    numbers = numbers
        .filter { it.raw.isNotBlank() }
        .map { StaffNumber(id = it.id, raw = it.raw, label = it.label) },
)
