package com.plna.smartcheck.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.ui.home.HomeViewModel
import com.plna.smartcheck.ui.profile.ProfileViewModel
import com.plna.smartcheck.ui.scan.QrScanViewModel
import com.plna.smartcheck.ui.statistics.StatisticsViewModel

class AppViewModelFactory(
    private val app: PlnaApplication,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val container = app.container
        return when {
            modelClass.isAssignableFrom(HomeViewModel::class.java) -> {
                HomeViewModel(
                    localExpenseRepository = container.localExpenseRepository,
                    syncManager = container.syncManager,
                ) as T
            }
            modelClass.isAssignableFrom(QrScanViewModel::class.java) -> {
                QrScanViewModel(
                    application = app,
                    localExpenseRepository = container.localExpenseRepository,
                    draftTextStore = container.draftTextStore,
                    syncManager = container.syncManager,
                ) as T
            }
            modelClass.isAssignableFrom(ProfileViewModel::class.java) -> {
                ProfileViewModel(
                    accountRepository = container.accountRepository,
                    languageController = container.languageController,
                    themePreferences = container.themePreferences,
                    syncManager = container.syncManager,
                ) as T
            }
            modelClass.isAssignableFrom(StatisticsViewModel::class.java) -> {
                StatisticsViewModel(
                    localExpenseRepository = container.localExpenseRepository,
                ) as T
            }
            else -> throw IllegalArgumentException("Unknown ViewModel: ${modelClass.name}")
        }
    }
}
