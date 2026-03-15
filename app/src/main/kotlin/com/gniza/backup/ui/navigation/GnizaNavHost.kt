package com.gniza.backup.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gniza.backup.ui.screens.help.HelpScreen
import com.gniza.backup.ui.screens.logs.LogDetailScreen
import com.gniza.backup.ui.screens.logs.LogListScreen
import com.gniza.backup.ui.screens.schedules.ScheduleEditScreen
import com.gniza.backup.ui.screens.schedules.ScheduleListScreen
import com.gniza.backup.ui.screens.schedules.ScheduleProgressScreen
import com.gniza.backup.ui.screens.sources.SourceEditScreen
import com.gniza.backup.ui.screens.sources.SourceListScreen
import com.gniza.backup.ui.screens.servers.ServerEditScreen
import com.gniza.backup.ui.screens.servers.ServerListScreen
import com.gniza.backup.ui.screens.qrscanner.QrScannerScreen
import com.gniza.backup.ui.screens.settings.SettingsScreen
import com.gniza.backup.ui.screens.sshkeys.SshKeyScreen
import com.gniza.backup.ui.screens.wizard.SetupWizardScreen

@Composable
fun GnizaNavHost(
    navController: NavHostController,
    startDestination: String = Screen.ScheduleList.route,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Screen.ServerList.route) {
            ServerListScreen(navController = navController)
        }

        composable(
            route = Screen.ServerEdit.route,
            arguments = listOf(navArgument("serverId") { type = NavType.LongType })
        ) { backStackEntry ->
            val serverId = backStackEntry.arguments?.getLong("serverId") ?: 0L
            ServerEditScreen(
                navController = navController,
                serverId = serverId
            )
        }

        composable(Screen.SourceList.route) {
            SourceListScreen(navController = navController)
        }

        composable(
            route = Screen.SourceEdit.route,
            arguments = listOf(navArgument("sourceId") { type = NavType.LongType })
        ) { backStackEntry ->
            val sourceId = backStackEntry.arguments?.getLong("sourceId") ?: 0L
            SourceEditScreen(
                navController = navController,
                sourceId = sourceId
            )
        }

        composable(Screen.ScheduleList.route) {
            ScheduleListScreen(navController = navController)
        }

        composable(
            route = Screen.ScheduleEdit.route,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getLong("scheduleId") ?: 0L
            ScheduleEditScreen(
                navController = navController,
                scheduleId = scheduleId
            )
        }

        composable(
            route = Screen.ScheduleProgress.route,
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType })
        ) { backStackEntry ->
            val scheduleId = backStackEntry.arguments?.getLong("scheduleId") ?: 0L
            ScheduleProgressScreen(
                scheduleId = scheduleId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.LogList.route) {
            LogListScreen(navController = navController)
        }

        composable(
            route = Screen.LogDetail.route,
            arguments = listOf(navArgument("logId") { type = NavType.LongType })
        ) {
            LogDetailScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SshKeys.route) {
            SshKeyScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(navController = navController)
        }

        composable(Screen.SetupWizard.route) {
            SetupWizardScreen(
                navController = navController,
                onComplete = {
                    navController.navigate(Screen.ScheduleList.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onSkip = {
                    navController.navigate(Screen.ScheduleList.route) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.QrScanner.route) {
            QrScannerScreen(navController = navController)
        }

        composable(Screen.Help.route) {
            HelpScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
