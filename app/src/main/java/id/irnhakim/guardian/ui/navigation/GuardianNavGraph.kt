package id.irnhakim.guardian.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import id.irnhakim.guardian.ui.screens.home.HomeScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
}

@Composable
fun GuardianNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen()
        }
    }
}
