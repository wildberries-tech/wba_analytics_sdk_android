package ru.wildanalytics.pub.analytics.sdk.demo.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.core.content.ContextCompat
import androidx.navigation.compose.rememberNavController

class AppActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()
        setContent {
            MaterialTheme {
                val resumedSuite = remember {
                    if (savedInstanceState == null) (application as DemoApplication).takeResumedSuite() else null
                }
                DemoNavHost(navController = rememberNavController(), resumedSuite = resumedSuite)
            }
        }
    }

    // Уведомления нужны Chucker.
    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
