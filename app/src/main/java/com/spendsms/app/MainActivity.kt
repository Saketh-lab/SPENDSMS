package com.spendsms.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.spendsms.app.platform.security.SensitiveWindowPolicy
import com.spendsms.app.presentation.navigation.SpendSmsNavHost
import com.spendsms.app.presentation.theme.SpendSmsTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SensitiveWindowPolicy.disableScreenshots(window)
        enableEdgeToEdge()
        setContent {
            SpendSmsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpendSmsNavHost()
                }
            }
        }
    }
}
