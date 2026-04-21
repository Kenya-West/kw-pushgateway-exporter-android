package com.kw.pushgatewayexporter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kw.pushgatewayexporter.ui.navigation.AppNavigation
import com.kw.pushgatewayexporter.ui.theme.PushgatewayExporterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PushgatewayExporterTheme {
                AppNavigation()
            }
        }
    }
}
