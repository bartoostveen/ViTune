package app.vitune.android.ui.screens.settings

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.internal
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.query
import app.vitune.android.service.PlayerService
import app.vitune.android.transaction
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.intent
import app.vitune.android.utils.toast
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

@SuppressLint("RestrictedApi")
@Route
@Composable
fun DatabaseSettings() = with(DataPreferences) {
    val context = LocalContext.current

    val eventsCount by remember { Database.eventsCount().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val blacklistLength by remember { Database.blacklistLength().distinctUntilChanged() }
        .collectAsState(initial = 0)

    val backupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(mimeType = "application/vnd.sqlite3")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.checkpoint()

            context.applicationContext.contentResolver.openOutputStream(uri)?.use { output ->
                FileInputStream(Database.internal.path).use { input -> input.copyTo(output) }
            }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult

        query {
            Database.checkpoint()
            Database.internal.close()

            with(context) {
                applicationContext.contentResolver.openInputStream(uri)
                    ?.use { inputStream ->
                        FileOutputStream(Database.internal.path).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                stopService(intent<PlayerService>())
            }

            exitProcess(0)
        }
    }

    SettingsCategoryScreen(title = stringResource(R.string.database)) {
        SettingsGroup(title = stringResource(R.string.cleanup)) {
            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_history),
                text = stringResource(R.string.pause_playback_history_description),
                isChecked = pauseHistory,
                onCheckedChange = { pauseHistory = !pauseHistory }
            )

            AnimatedVisibility(visible = pauseHistory) {
                SettingsDescription(
                    text = stringResource(R.string.pause_playback_history_warning),
                    important = true
                )
            }

            AnimatedVisibility(visible = !(pauseHistory && eventsCount == 0)) {
                SettingsEntry(
                    title = stringResource(R.string.reset_quick_picks),
                    text = if (eventsCount > 0) pluralStringResource(
                        R.plurals.format_reset_quick_picks_amount,
                        eventsCount,
                        eventsCount
                    )
                    else stringResource(R.string.quick_picks_empty),
                    onClick = { query(Database::clearEvents) },
                    isEnabled = eventsCount > 0
                )
            }

            SwitchSettingsEntry(
                title = stringResource(R.string.pause_playback_time),
                text = stringResource(
                    R.string.format_pause_playback_time_description,
                    topListLength
                ),
                isChecked = pausePlaytime,
                onCheckedChange = { pausePlaytime = !pausePlaytime }
            )

            SettingsEntry(
                title = stringResource(R.string.reset_blacklist),
                text = if (blacklistLength > 0) pluralStringResource(
                    R.plurals.format_reset_blacklist_description,
                    blacklistLength,
                    blacklistLength
                ) else stringResource(R.string.blacklist_empty),
                isEnabled = blacklistLength > 0,
                onClick = {
                    transaction {
                        Database.resetBlacklist()
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.backup),
            description = stringResource(R.string.backup_description)
        ) {
            val errorMsg = stringResource(R.string.no_file_chooser_installed)

            SettingsEntry(
                title = stringResource(R.string.backup),
                text = stringResource(R.string.backup_action_description),
                onClick = {
                    val dateFormat = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault())

                    try {
                        backupLauncher.launch("ViTune_backup_${dateFormat.format(Date())}.db")
                    } catch (_: ActivityNotFoundException) {
                        context.toast(errorMsg)
                    }
                }
            )
        }
        SettingsGroup(
            title = stringResource(R.string.restore),
            description = stringResource(R.string.restore_warning),
            important = true
        ) {
            val errorMsg = stringResource(R.string.no_file_chooser_installed)

            SettingsEntry(
                title = stringResource(R.string.restore),
                text = stringResource(R.string.restore_description),
                onClick = {
                    try {
                        restoreLauncher.launch(
                            arrayOf(
                                "application/vnd.sqlite3",
                                "application/x-sqlite3",
                                "application/octet-stream"
                            )
                        )
                    } catch (_: ActivityNotFoundException) {
                        context.toast(errorMsg)
                    }
                }
            )
        }
    }
}
