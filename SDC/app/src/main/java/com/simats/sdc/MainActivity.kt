package com.simats.sdc

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.simats.sdc.core.theme.OralCareTheme
import com.simats.sdc.presentation.auth.AuthViewModel
import com.simats.sdc.presentation.navigation.OralCareAppRoot
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Native Android edge-to-edge system bars with transparent background and dark icons over light blue
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = Color.TRANSPARENT,
                darkScrim = Color.TRANSPARENT,
            ),
        )
        setContent {
            val authViewModel: AuthViewModel = hiltViewModel()
            val session by authViewModel.session.collectAsState()
            OralCareTheme(darkTheme = session.darkMode) {
                OralCareAppRoot(authViewModel = authViewModel)
            }
        }
    }
}
