package id.irnhakim.guardian.ui.navigation

import androidx.compose.runtime.*
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.*
import id.irnhakim.guardian.ui.screens.setup.SetupScreen
import id.irnhakim.guardian.ui.screens.home.HomeScreen
import id.irnhakim.guardian.ui.viewmodel.MainViewModel

sealed class Screen(val route: String) {
    object Setup : Screen("setup")
    object Home : Screen("home")
}

@Composable
fun GuardianNavGraph() {
    val navController = rememberNavController()
    val viewModel: MainViewModel = hiltViewModel()
    val isRegistered by viewModel.isRegistered.collectAsState()

    val startDest = if (isRegistered) Screen.Home.route else Screen.Setup.route

    NavHost(navController = navController, startDestination = startDest) {
        composable(Screen.Setup.route) {
            SetupScreen(onSetupComplete = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(Screen.Setup.route) { inclusive = true }
                }
            })
        }
        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
