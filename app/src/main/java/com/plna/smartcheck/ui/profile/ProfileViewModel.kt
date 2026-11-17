package com.plna.smartcheck.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.plna.smartcheck.data.locale.AppLanguage
import com.plna.smartcheck.data.locale.LanguageController
import com.plna.smartcheck.data.local.UserSessionEntity
import com.plna.smartcheck.data.repository.AccountRepository
import com.plna.smartcheck.data.sync.ExpenseSyncManager
import com.plna.smartcheck.data.theme.AppThemeMode
import com.plna.smartcheck.data.theme.ThemePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AuthMode { LOGIN, REGISTER }

enum class ProfilePage { MAIN, ABOUT }

data class ProfileUiState(
    val session: UserSessionEntity? = null,
    val isLoggedIn: Boolean = false,
    val page: ProfilePage = ProfilePage.MAIN,
    val authMode: AuthMode = AuthMode.REGISTER,
    val displayNameInput: String = "",
    val accountIdInput: String = "",
    val language: AppLanguage = AppLanguage.RU,
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isLoading: Boolean = false,
    val loadingTitle: String = "",
    val loadingSubtitle: String? = null,
    val infoMessage: String? = null,
    val errorMessage: String? = null,
    val showLanguagePicker: Boolean = false,
    val showThemePicker: Boolean = false,
    val showEditName: Boolean = false,
)

class ProfileViewModel(
    private val accountRepository: AccountRepository,
    private val languageController: LanguageController,
    private val themePreferences: ThemePreferences,
    private val syncManager: ExpenseSyncManager,
) : ViewModel() {

    private val _ui = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = combine(
        _ui,
        accountRepository.sessionFlow,
        languageController.languageFlow,
        themePreferences.modeFlow,
    ) { ui, session, language, themeMode ->
        ui.copy(
            session = session,
            isLoggedIn = session != null,
            language = language,
            themeMode = themeMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ProfileUiState(),
    )

    fun setAuthMode(mode: AuthMode) {
        _ui.update { it.copy(authMode = mode, errorMessage = null) }
    }

    fun onDisplayNameInput(value: String) {
        _ui.update { it.copy(displayNameInput = value) }
    }

    fun onAccountIdInput(value: String) {
        val digits = value.filter { it.isDigit() }.take(16)
        _ui.update { it.copy(accountIdInput = digits) }
    }

    fun openAbout() {
        _ui.update { it.copy(page = ProfilePage.ABOUT) }
    }

    fun backToMain() {
        _ui.update { it.copy(page = ProfilePage.MAIN) }
    }

    fun register() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isLoading = true,
                    loadingTitle = "Создаём аккаунт",
                    loadingSubtitle = "Почти готово…",
                    errorMessage = null,
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    accountRepository.register(_ui.value.displayNameInput)
                }
            }.onSuccess { session ->
                _ui.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = "Готово! Ваш ID: ${AccountRepository.formatAccountId(session.accountId)}. Нажмите на номер, чтобы скопировать.",
                        accountIdInput = "",
                    )
                }
                // облако в фоне
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        accountRepository.syncPendingAccount()
                        syncManager.syncNow()
                    }
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка регистрации")
                }
            }
        }
    }

    fun login() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isLoading = true,
                    loadingTitle = "Вход",
                    loadingSubtitle = "Проверяем ID…",
                    errorMessage = null,
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    accountRepository.login(_ui.value.accountIdInput)
                }
            }.onSuccess { session ->
                languageController.setLanguage(AppLanguage.fromCode(session.languageCode))
                _ui.update {
                    it.copy(
                        isLoading = false,
                        accountIdInput = "",
                        infoMessage = "Вход выполнен. Чеки подтянем в фоне.",
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    val imported = runCatching {
                        accountRepository.pullExpensesFromCloud()
                    }.getOrDefault(0)
                    if (imported > 0) {
                        _ui.update {
                            it.copy(infoMessage = "Загружено чеков с облака: $imported")
                        }
                    }
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка входа")
                }
            }
        }
    }

    fun openEditName() {
        _ui.update {
            it.copy(showEditName = true, displayNameInput = it.session?.displayName.orEmpty())
        }
    }

    fun closeEditName() {
        _ui.update { it.copy(showEditName = false) }
    }

    fun saveDisplayName() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isLoading = true,
                    loadingTitle = "Сохраняем имя",
                    loadingSubtitle = null,
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    accountRepository.updateDisplayName(_ui.value.displayNameInput)
                }
            }.onSuccess {
                _ui.update {
                    it.copy(isLoading = false, showEditName = false, infoMessage = "Имя обновлено")
                }
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching { accountRepository.syncPendingAccount() }
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(isLoading = false, errorMessage = e.message)
                }
            }
        }
    }

    fun openLanguagePicker() {
        _ui.update { it.copy(showLanguagePicker = true) }
    }

    fun closeLanguagePicker() {
        _ui.update { it.copy(showLanguagePicker = false) }
    }

    /**
     * @return true если Activity нужно пересоздать для применения строк.
     */
    suspend fun selectLanguage(language: AppLanguage): Boolean {
        languageController.setLanguage(language)
        withContext(Dispatchers.IO) {
            accountRepository.updateLanguage(language.code)
            runCatching { accountRepository.syncPendingAccount() }
        }
        _ui.update { it.copy(showLanguagePicker = false) }
        return true
    }

    fun openThemePicker() {
        _ui.update { it.copy(showThemePicker = true) }
    }

    fun closeThemePicker() {
        _ui.update { it.copy(showThemePicker = false) }
    }

    fun selectTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            themePreferences.setMode(mode)
            _ui.update { it.copy(showThemePicker = false) }
        }
    }

    fun refreshFromCloud() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    isLoading = true,
                    loadingTitle = "Синхронизация",
                    loadingSubtitle = "Подождите…",
                )
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    syncManager.syncNow()
                    accountRepository.pullExpensesFromCloud()
                }
            }.onSuccess { imported ->
                _ui.update {
                    it.copy(
                        isLoading = false,
                        infoMessage = if (imported > 0) {
                            "Готово. Новых чеков: $imported"
                        } else {
                            "Всё уже актуально"
                        },
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            accountRepository.logout()
            _ui.update {
                it.copy(
                    authMode = AuthMode.LOGIN,
                    page = ProfilePage.MAIN,
                    infoMessage = "Вы вышли. Чеки на этом телефоне остались.",
                )
            }
        }
    }

    fun clearMessages() {
        _ui.update { it.copy(errorMessage = null, infoMessage = null) }
    }

    fun showCopiedMessage() {
        _ui.update { it.copy(infoMessage = "ID скопирован. Сохраните его в надёжном месте.") }
    }
}
