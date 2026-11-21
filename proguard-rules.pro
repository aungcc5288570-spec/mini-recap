package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.example.ui.MiniRecapScreen
import com.example.ui.theme.MiniRecapTheme
import com.example.ui.theme.Obsidian950

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Obsidian950.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Obsidian950.toArgb()),
        )
        setContent {
            MiniRecapTheme {
                MiniRecapScreen()
            }
        }
    }
}
