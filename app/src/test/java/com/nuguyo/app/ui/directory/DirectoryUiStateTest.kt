package com.nuguyo.app.ui.directory

import com.nuguyo.app.domain.model.Employee
import com.nuguyo.app.domain.model.EmployeeSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryUiStateTest {

    private fun employee(id: String, source: EmployeeSource = EmployeeSource.LOCAL) =
        Employee(id = id, name = id, source = source)

    @Test
    fun `아무것도 고르지 않으면 선택 모드가 아니다`() {
        val state = DirectoryUiState(employees = listOf(employee("a")))

        assertFalse(state.selectionMode)
    }

    @Test
    fun `하나라도 고르면 선택 모드가 된다`() {
        val state = DirectoryUiState(
            employees = listOf(employee("a"), employee("b")),
            selectedIds = setOf("a"),
        )

        assertTrue(state.selectionMode)
        assertFalse(state.allVisibleSelected)
    }

    @Test
    fun `검색 중이면 전체 선택은 보이는 것만 따진다`() {
        // 검색으로 b 만 보이는 상황. b 를 골랐으면 '전체 선택'된 것으로 본다.
        val state = DirectoryUiState(
            employees = listOf(employee("b")),
            totalCount = 5,
            selectedIds = setOf("b"),
        )

        assertTrue(state.allVisibleSelected)
    }

    @Test
    fun `목록이 비어 있으면 전체 선택 상태가 아니다`() {
        // employees 가 비었을 때 all 이 true 를 돌려주면 '전체 선택 해제' 아이콘이 뜬다.
        val state = DirectoryUiState(employees = emptyList(), selectedIds = setOf("a"))

        assertFalse(state.allVisibleSelected)
    }

    @Test
    fun `시트에서 온 직원이 선택되면 경고 대상이다`() {
        val state = DirectoryUiState(
            employees = listOf(
                employee("a"),
                employee("b", EmployeeSource.SHEET),
            ),
            selectedIds = setOf("b"),
        )

        assertTrue(state.selectionHasSheetEmployees)
    }

    @Test
    fun `앱에서 만든 직원만 선택하면 경고하지 않는다`() {
        val state = DirectoryUiState(
            employees = listOf(
                employee("a"),
                employee("b", EmployeeSource.SHEET),
            ),
            selectedIds = setOf("a"),
        )

        assertFalse(state.selectionHasSheetEmployees)
    }
}
