package com.nuguyo.app.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nuguyo.app.ui.directory.DirectoryScreen
import com.nuguyo.app.ui.editor.EmployeeEditorScreen
import com.nuguyo.app.ui.history.HistoryScreen
import com.nuguyo.app.ui.permissions.PermissionsScreen
import com.nuguyo.app.ui.settings.SettingsScreen
import com.nuguyo.app.ui.theme.NuguyoTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 팝업에서 "상세 보기"/"직원으로 등록"을 눌러 들어온 경우.
        val employeeId = intent?.getStringExtra(EXTRA_EMPLOYEE_ID)
        val prefillNumber = intent?.getStringExtra(EXTRA_PREFILL_NUMBER)

        setContent {
            NuguyoTheme {
                NuguyoNavHost(
                    deepLinkEmployeeId = employeeId,
                    deepLinkPrefillNumber = prefillNumber,
                )
            }
        }
    }

    companion object {
        const val EXTRA_EMPLOYEE_ID = "com.nuguyo.app.extra.EMPLOYEE_ID"
        const val EXTRA_PREFILL_NUMBER = "com.nuguyo.app.extra.PREFILL_NUMBER"
    }
}

private object Route {
    const val DIRECTORY = "directory"
    const val HISTORY = "history"
    const val PERMISSIONS = "permissions"
    const val SETTINGS = "settings"
    const val EDITOR = "editor?id={id}&number={number}"

    fun editor(id: String? = null, number: String? = null): String {
        // 번호에는 +, -, 공백이 섞여 오므로 라우트에 넣기 전에 인코딩한다.
        val idPart = id?.let { Uri.encode(it) }.orEmpty()
        val numberPart = number?.let { Uri.encode(it) }.orEmpty()
        return "editor?id=$idPart&number=$numberPart"
    }
}

@Composable
private fun NuguyoNavHost(
    deepLinkEmployeeId: String?,
    deepLinkPrefillNumber: String?,
) {
    val navController = rememberNavController()

    // 명부를 백스택 밑에 깔아 두고 편집 화면을 얹는다. 뒤로 가면 명부가 나온다.
    LaunchedEffect(deepLinkEmployeeId, deepLinkPrefillNumber) {
        if (deepLinkEmployeeId != null) {
            navController.navigate(Route.editor(id = deepLinkEmployeeId))
        } else if (deepLinkPrefillNumber != null) {
            navController.navigate(Route.editor(number = deepLinkPrefillNumber))
        }
    }

    NavHost(navController = navController, startDestination = Route.DIRECTORY) {
        composable(Route.DIRECTORY) {
            DirectoryScreen(
                onAddEmployee = { navController.navigate(Route.editor()) },
                onOpenEmployee = { navController.navigate(Route.editor(id = it)) },
                onOpenHistory = { navController.navigate(Route.HISTORY) },
                onOpenPermissions = { navController.navigate(Route.PERMISSIONS) },
                onOpenSettings = { navController.navigate(Route.SETTINGS) },
            )
        }
        composable(
            route = Route.EDITOR,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("number") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            EmployeeEditorScreen(
                employeeId = entry.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                prefillNumber = entry.arguments?.getString("number")?.takeIf { it.isNotBlank() },
                onDone = { navController.popBackStack() },
            )
        }
        composable(Route.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenEmployee = { navController.navigate(Route.editor(id = it)) },
            )
        }
        composable(Route.PERMISSIONS) {
            PermissionsScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
