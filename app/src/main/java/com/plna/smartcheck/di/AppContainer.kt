package com.plna.smartcheck.di

import android.content.Context
import androidx.room.Room
import com.plna.smartcheck.data.local.AppDatabase
import com.plna.smartcheck.data.local.DraftTextStore
import com.plna.smartcheck.data.locale.LanguageController
import com.plna.smartcheck.data.locale.LanguageStore
import com.plna.smartcheck.data.network.NetworkMonitor
import com.plna.smartcheck.data.repository.AccountRepository
import com.plna.smartcheck.data.repository.ExpenseRepository
import com.plna.smartcheck.data.repository.LocalExpenseRepository
import com.plna.smartcheck.data.sync.ExpenseSyncManager
import com.plna.smartcheck.data.theme.ThemePreferences

/**
 * Ручной DI-контейнер приложения.
 *
 * Создаётся один раз в [com.plna.smartcheck.PlnaApplication].
 * Здесь собираются Room, репозитории, синк, язык и тема.
 *
 * Важно: OCR-модели Tesseract живут НЕ в Room, а в filesDir
 * (см. [com.plna.smartcheck.data.scan.RussianOcrEngine]).
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val application = appContext as android.app.Application

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "plna_smartcheck.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    val expenseDao get() = database.expenseDao()
    val userSessionDao get() = database.userSessionDao()
    val pendingCloudDeleteDao get() = database.pendingCloudDeleteDao()
    val draftTextStore = DraftTextStore(appContext)
    val languageStore = LanguageStore(appContext)
    val languageController = LanguageController(
        application = application,
        store = languageStore,
    )
    val themePreferences = ThemePreferences(appContext)
    val networkMonitor = NetworkMonitor(appContext)
    val expenseRepository = ExpenseRepository()
    val localExpenseRepository = LocalExpenseRepository(
        expenseDao = expenseDao,
        pendingCloudDeleteDao = pendingCloudDeleteDao,
        networkMonitor = networkMonitor,
        remoteRepository = expenseRepository,
    )
    val accountRepository = AccountRepository(
        sessionDao = userSessionDao,
        expenseDao = expenseDao,
        networkMonitor = networkMonitor,
        expenseRepository = expenseRepository,
    )
    val syncManager = ExpenseSyncManager(
        expenseDao = expenseDao,
        expenseRepository = expenseRepository,
        networkMonitor = networkMonitor,
        accountRepository = accountRepository,
        localExpenseRepository = localExpenseRepository,
    )
}
