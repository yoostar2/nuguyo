package com.nuguyo.app.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuguyo.app.data.EmployeeRepository
import com.nuguyo.app.data.deleteAppStoragePhotos
import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.EmployeeSource
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DirectoryUiState(
    val query: String = "",
    val employees: List<Employee> = emptyList(),
    val totalCount: Int = 0,
    val selectedIds: Set<String> = emptySet(),
    val loaded: Boolean = false,
) {
    val selectionMode: Boolean get() = selectedIds.isNotEmpty()

    /** 화면에 보이는 것이 전부 골라져 있는지. '전체 선택' 버튼의 상태가 된다. */
    val allVisibleSelected: Boolean
        get() = employees.isNotEmpty() && employees.all { it.id in selectedIds }

    /** 시트에서 온 직원을 지우면 다음 동기화 때 되살아난다. 지우기 전에 알려 줘야 한다. */
    val selectionHasSheetEmployees: Boolean
        get() = employees.any { it.id in selectedIds && it.source == EmployeeSource.SHEET }
}

class DirectoryViewModel(private val employees: EmployeeRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    private val selectedIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * 검색은 메모리에서 처리한다. 사내 명부는 많아도 수천 건이고, FTS 테이블을
     * 따로 관리하는 비용보다 이쪽이 싸다.
     */
    val uiState: StateFlow<DirectoryUiState> =
        combine(employees.observeAll(), query, selectedIds) { list, keyword, selected ->
            val existingIds = list.mapTo(mutableSetOf()) { it.id }
            DirectoryUiState(
                query = keyword,
                employees = list.filter { it.matches(keyword) },
                totalCount = list.size,
                // 동기화로 사라진 직원이 선택된 채 남아 개수가 어긋나지 않게 걸러낸다.
                selectedIds = selected intersect existingIds,
                loaded = true,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DirectoryUiState(),
        )

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun toggleSelection(id: String) {
        selectedIds.value = selectedIds.value.let { current ->
            if (id in current) current - id else current + id
        }
    }

    /** 검색 중이면 '전체'는 화면에 보이는 것을 뜻한다. */
    fun toggleSelectAllVisible() {
        val visible = uiState.value.employees.map { it.id }.toSet()
        selectedIds.value = if (uiState.value.allVisibleSelected) {
            selectedIds.value - visible
        } else {
            selectedIds.value + visible
        }
    }

    fun clearSelection() {
        selectedIds.value = emptySet()
    }

    fun deleteSelected() {
        val targets = uiState.value.selectedIds
        if (targets.isEmpty()) return
        val photos = uiState.value.employees
            .filter { it.id in targets }
            .map { it.photoUri }

        viewModelScope.launch {
            employees.delete(targets)
            deleteAppStoragePhotos(photos)
            selectedIds.value = emptySet()
        }
    }

    /** 검색어와 무관하게 명부를 통째로 비운다. */
    fun deleteEverything() {
        viewModelScope.launch {
            val photos = employees.observeAll().first().map { it.photoUri }
            employees.deleteAll()
            deleteAppStoragePhotos(photos)
            selectedIds.value = emptySet()
        }
    }
}

private fun Employee.matches(keyword: String): Boolean {
    val needle = keyword.trim()
    if (needle.isEmpty()) return true

    // 숫자만 입력했으면 번호를 찾는 것으로 본다.
    val digits = needle.filter { it.isDigit() }
    if (digits.length >= 2 && digits.length == needle.filter { !it.isWhitespace() && it != '-' }.length) {
        return numbers.any { PhoneNumberNormalizer.normalize(it.raw).digits.contains(digits) }
    }

    return listOfNotNull(name, department, title, employeeNo, memo)
        .any { it.contains(needle, ignoreCase = true) } ||
        tags.any { it.contains(needle, ignoreCase = true) }
}
