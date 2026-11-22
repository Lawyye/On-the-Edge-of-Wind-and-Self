package com.nutritiontracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.graphics.toArgb
import com.nutritiontracker.ui.navigation.AppRoot
import com.nutritiontracker.ui.theme.DarkAppColors
import com.nutritiontracker.ui.theme.NutritionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bar = DarkAppColors.background.toArgb()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(bar),
            navigationBarStyle = SystemBarStyle.dark(DarkAppColors.surface.toArgb()),
        )
        setContent {
            NutritionTheme {
                AppRoot()
            }
        }
    }
}
