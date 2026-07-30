package com.nuguyo.app.ui.directory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuguyo.app.data.EmployeeRepository
import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.phone.PhoneNumberNormalizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DirectoryUiState(
    val query: String = "",
    val employees: List<Employee> = emptyList(),
    val totalCount: Int = 0,
    val loaded: Boolean = false,
)

class DirectoryViewModel(private val employees: EmployeeRepository) : ViewModel() {

    private val query = MutableStateFlow("")

    /**
     * 검색은 메모리에서 처리한다. 사내 명부는 많아도 수천 건이고, FTS 테이블을
     * 따로 관리하는 비용보다 이쪽이 싸다.
     */
    val uiState: StateFlow<DirectoryUiState> =
        combine(employees.observeAll(), query) { list, keyword ->
            DirectoryUiState(
                query = keyword,
                employees = list.filter { it.matches(keyword) },
                totalCount = list.size,
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

    fun delete(id: String) {
        viewModelScope.launch { employees.delete(id) }
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
