package com.tg.automation.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.tg.automation.service.AutomationService
import com.tg.automation.ui.screens.AppRoot
import com.tg.automation.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { AppTheme { AppRoot() } }
    }

    override fun onResume() {
        super.onResume()
        AutomationService.sync(this)
    }
}
