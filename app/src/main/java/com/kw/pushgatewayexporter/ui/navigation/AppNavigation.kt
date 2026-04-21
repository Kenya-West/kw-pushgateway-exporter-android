package com.kw.pushgatewayexporter.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.kw.pushgatewayexporter.ui.screens.*

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "main") {
        composable("main") { MainScreen(navController) }
        composable("config") { ConfigScreen(navController) }
        composable("preview") { PreviewScreen(navController) }
        composable("catalog") { MetricCatalogScreen(navController) }
        composable("samples") { SamplesScreen(navController) }
    }
}
