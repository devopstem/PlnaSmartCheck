package com.plna.smartcheck

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plna.smartcheck.data.theme.AppThemeMode
import com.plna.smartcheck.ui.home.HomeScreen
import com.plna.smartcheck.ui.profile.ProfileScreen
import com.plna.smartcheck.ui.scan.QrScanScreen
import com.plna.smartcheck.ui.statistics.StatisticsScreen
import com.plna.smartcheck.ui.theme.PlnaSmartCheckTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = LocalContext.current.applicationContext as PlnaApplication
            val themeMode by app.container.themePreferences.modeFlow
                .collectAsStateWithLifecycle(initialValue = AppThemeMode.SYSTEM)
            val darkTheme = when (themeMode) {
                AppThemeMode.LIGHT -> false
                AppThemeMode.DARK -> true
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            PlnaSmartCheckTheme(darkTheme = darkTheme) {
                PlnaSmartCheckApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun PlnaSmartCheckApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.HOME) }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach { destination ->
                item(
                    icon = {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    },
                    label = { Text(stringResource(destination.labelRes)) },
                    selected = destination == currentDestination,
                    onClick = { currentDestination = destination },
                )
            }
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            when (currentDestination) {
                AppDestinations.HOME -> HomeScreen(
                    onOpenProfile = { currentDestination = AppDestinations.PROFILE },
                )
                AppDestinations.SCAN -> QrScanScreen()
                AppDestinations.STATS -> StatisticsScreen()
                AppDestinations.PROFILE -> ProfileScreen()
            }
        }
    }
}

enum class AppDestinations(
    val labelRes: Int,
    val icon: ImageVector,
) {
    HOME(R.string.nav_home, Icons.Filled.Home),
    SCAN(R.string.nav_scan, Icons.Filled.QrCodeScanner),
    STATS(R.string.nav_stats, Icons.Filled.BarChart),
    PROFILE(R.string.nav_profile, Icons.Filled.Person),
}
