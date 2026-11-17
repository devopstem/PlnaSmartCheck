package com.plna.smartcheck.ui.profile

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.plna.smartcheck.PlnaApplication
import com.plna.smartcheck.R
import com.plna.smartcheck.data.locale.AppLanguage
import com.plna.smartcheck.data.repository.AccountRepository
import com.plna.smartcheck.data.theme.AppThemeMode
import com.plna.smartcheck.ui.AppViewModelFactory
import com.plna.smartcheck.ui.components.PlnaLoadingOverlay
import com.plna.smartcheck.ui.components.PlnaScreenTopBar
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val app = context.applicationContext as PlnaApplication
    val viewModel: ProfileViewModel = viewModel(factory = AppViewModelFactory(app))
    val ui by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(ui.errorMessage, ui.infoMessage) {
        val msg = ui.errorMessage ?: ui.infoMessage ?: return@LaunchedEffect
        snackbar.showSnackbar(msg)
        viewModel.clearMessages()
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = contentPadding.calculateBottomPadding()),
            topBar = {
                when (ui.page) {
                    ProfilePage.ABOUT -> PlnaScreenTopBar(
                        title = stringResource(R.string.about_title),
                        onBack = viewModel::backToMain,
                    )
                    ProfilePage.MAIN -> PlnaScreenTopBar(title = stringResource(R.string.profile_title))
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { inner ->
            when (ui.page) {
                ProfilePage.ABOUT -> AboutContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(inner)
                        .padding(horizontal = 20.dp),
                )
                ProfilePage.MAIN -> {
                    if (ui.isLoggedIn && ui.session != null) {
                        LoggedInProfile(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                                .padding(horizontal = 16.dp),
                            displayName = ui.session!!.displayName,
                            accountId = ui.session!!.accountId,
                            language = ui.language,
                            themeMode = ui.themeMode,
                            onEditName = viewModel::openEditName,
                            onLanguage = viewModel::openLanguagePicker,
                            onTheme = viewModel::openThemePicker,
                            onSync = viewModel::refreshFromCloud,
                            onAbout = viewModel::openAbout,
                            onLogout = viewModel::logout,
                            onCopyId = {
                                copyAccountId(context, ui.session!!.accountId)
                                viewModel.showCopiedMessage()
                            },
                        )
                    } else {
                        AuthContent(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(inner)
                                .padding(horizontal = 20.dp),
                            mode = ui.authMode,
                            displayName = ui.displayNameInput,
                            accountId = ui.accountIdInput,
                            language = ui.language,
                            onMode = viewModel::setAuthMode,
                            onDisplayName = viewModel::onDisplayNameInput,
                            onAccountId = viewModel::onAccountIdInput,
                            onRegister = viewModel::register,
                            onLogin = viewModel::login,
                            onLanguage = viewModel::openLanguagePicker,
                            onAbout = viewModel::openAbout,
                        )
                    }
                }
            }
        }

        PlnaLoadingOverlay(
            visible = ui.isLoading,
            title = ui.loadingTitle.ifBlank { stringResource(R.string.please_wait) },
            subtitle = ui.loadingSubtitle ?: stringResource(R.string.loading_hint),
        )

        if (ui.showLanguagePicker) {
            LanguageDialog(
                current = ui.language,
                onSelect = { lang ->
                    scope.launch {
                        viewModel.selectLanguage(lang)
                        activity?.recreate()
                    }
                },
                onDismiss = viewModel::closeLanguagePicker,
            )
        }
        if (ui.showThemePicker) {
            ThemeDialog(
                current = ui.themeMode,
                onSelect = viewModel::selectTheme,
                onDismiss = viewModel::closeThemePicker,
            )
        }
        if (ui.showEditName) {
            EditNameDialog(
                value = ui.displayNameInput,
                onChange = viewModel::onDisplayNameInput,
                onSave = viewModel::saveDisplayName,
                onDismiss = viewModel::closeEditName,
            )
        }
    }
}

private fun copyAccountId(context: Context, accountId: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("PLNA ID", accountId))
}

@Composable
private fun AboutContent(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = stringResource(
                R.string.about_version,
                com.plna.smartcheck.BuildConfig.VERSION_NAME,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AboutParagraph(stringResource(R.string.about_p1))
        AboutParagraph(stringResource(R.string.about_p2))
        AboutParagraph(stringResource(R.string.about_p3))
        AboutParagraph(stringResource(R.string.about_p4))
        AboutParagraph(stringResource(R.string.about_p5))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_author),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AboutParagraph(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun AuthContent(
    modifier: Modifier,
    mode: AuthMode,
    displayName: String,
    accountId: String,
    language: AppLanguage,
    onMode: (AuthMode) -> Unit,
    onDisplayName: (String) -> Unit,
    onAccountId: (String) -> Unit,
    onRegister: () -> Unit,
    onLogin: () -> Unit,
    onLanguage: () -> Unit,
    onAbout: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.auth_welcome),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.auth_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mode == AuthMode.REGISTER) {
                    Button(onClick = { onMode(AuthMode.REGISTER) }) {
                        Text(stringResource(R.string.action_register))
                    }
                    FilledTonalButton(onClick = { onMode(AuthMode.LOGIN) }) {
                        Text(stringResource(R.string.action_login))
                    }
                } else {
                    FilledTonalButton(onClick = { onMode(AuthMode.REGISTER) }) {
                        Text(stringResource(R.string.action_register))
                    }
                    Button(onClick = { onMode(AuthMode.LOGIN) }) {
                        Text(stringResource(R.string.action_login))
                    }
                }
            }
        }
        if (mode == AuthMode.REGISTER) {
            item {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = onDisplayName,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_display_name)) },
                    placeholder = { Text(stringResource(R.string.field_display_name_hint)) },
                    singleLine = true,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.auth_register_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                Button(
                    onClick = onRegister,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_create_account))
                }
            }
        } else {
            item {
                OutlinedTextField(
                    value = accountId,
                    onValueChange = onAccountId,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.field_account_id)) },
                    placeholder = { Text("0000 0000 0000 0000") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("${accountId.length}/16") },
                )
            }
            item {
                Button(
                    onClick = onLogin,
                    enabled = accountId.length == 16,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(stringResource(R.string.action_login))
                }
            }
        }
        item {
            SettingsGroup(title = stringResource(R.string.section_preferences)) {
                SettingsRow(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.language),
                    trailing = language.nativeLabel,
                    onClick = onLanguage,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_us),
                    onClick = onAbout,
                )
            }
        }
    }
}

@Composable
private fun LoggedInProfile(
    modifier: Modifier,
    displayName: String,
    accountId: String,
    language: AppLanguage,
    themeMode: AppThemeMode,
    onEditName: () -> Unit,
    onLanguage: () -> Unit,
    onTheme: () -> Unit,
    onSync: () -> Unit,
    onAbout: () -> Unit,
    onLogout: () -> Unit,
    onCopyId: () -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                ),
                shape = RoundedCornerShape(20.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onCopyId)
                                .padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = AccountRepository.formatAccountId(accountId),
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = stringResource(R.string.id_tap_to_copy),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = stringResource(R.string.id_tap_to_copy),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.section_account)) {
                SettingsRow(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.manage_profile),
                    onClick = onEditName,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.password_security),
                    trailing = stringResource(R.string.id_only_hint),
                    onClick = onCopyId,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.Translate,
                    title = stringResource(R.string.language),
                    trailing = language.nativeLabel,
                    onClick = onLanguage,
                )
            }
        }

        item {
            SettingsGroup(title = stringResource(R.string.section_preferences)) {
                SettingsRow(
                    icon = Icons.Filled.Contrast,
                    title = stringResource(R.string.theme),
                    trailing = themeModeLabel(themeMode),
                    onClick = onTheme,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.CloudSync,
                    title = stringResource(R.string.sync_cloud),
                    onClick = onSync,
                )
                HorizontalDivider()
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.about_us),
                    trailing = "PLNA",
                    onClick = onAbout,
                )
            }
        }

        item {
            TextButton(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.action_logout))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    content: @Composable () -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
        )
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column(Modifier.fillMaxWidth()) { content() }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    trailing: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        if (!trailing.isNullOrBlank()) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun themeModeLabel(mode: AppThemeMode): String = when (mode) {
    AppThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    AppThemeMode.LIGHT -> stringResource(R.string.theme_light)
    AppThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun LanguageDialog(
    current: AppLanguage,
    onSelect: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.language)) },
        text = {
            Column {
                AppLanguage.entries.forEach { lang ->
                    Text(
                        text = lang.nativeLabel + if (lang == current) " ✓" else "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(lang) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun ThemeDialog(
    current: AppThemeMode,
    onSelect: (AppThemeMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.theme)) },
        text = {
            Column {
                AppThemeMode.entries.forEach { mode ->
                    Text(
                        text = themeModeLabel(mode) + if (mode == current) " ✓" else "",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(mode) }
                            .padding(vertical = 12.dp),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun EditNameDialog(
    value: String,
    onChange: (String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.manage_profile)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                label = { Text(stringResource(R.string.field_display_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}
