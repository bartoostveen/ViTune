package app.vitune.android.ui.screens.home

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.models.Song
import app.vitune.android.preferences.OrderPreferences
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.android.transaction
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.AudioMediaCursor
import app.vitune.android.utils.hasPermission
import app.vitune.android.utils.medium
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isAtLeastAndroid13
import app.vitune.core.ui.utils.isCompositionLaunched
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private val permission = if (isAtLeastAndroid13) Manifest.permission.READ_MEDIA_AUDIO
else Manifest.permission.READ_EXTERNAL_STORAGE

@Route
@Composable
fun HomeLocalSongs(onSearchClick: () -> Unit) = with(OrderPreferences) {
    val context = LocalContext.current
    val (_, typography) = LocalAppearance.current

    var hasPermission by remember(isCompositionLaunched()) {
        mutableStateOf(context.applicationContext.hasPermission(permission))
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { hasPermission = it }
    )

    LaunchedEffect(hasPermission) {
        if (hasPermission) context.musicFilesAsFlow().collect()
    }

    if (hasPermission) HomeSongs(
        onSearchClick = onSearchClick,
        songProvider = {
            Database.songs(
                sortBy = localSongSortBy,
                sortOrder = localSongSortOrder,
                isLocal = true
            ).map { songs -> songs.filter { it.durationText != "0:00" } }
        },
        sortBy = localSongSortBy,
        setSortBy = { localSongSortBy = it },
        sortOrder = localSongSortOrder,
        setSortOrder = { localSongSortOrder = it },
        title = stringResource(R.string.local)
    ) else {
        LaunchedEffect(Unit) { launcher.launch(permission) }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BasicText(
                text = stringResource(R.string.media_permission_declined),
                modifier = Modifier.fillMaxWidth(0.75f),
                style = typography.m.medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            SecondaryTextButton(
                text = stringResource(R.string.open_settings),
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                    )
                }
            )
        }
    }
}

private val mediaScope = CoroutineScope(Dispatchers.IO + CoroutineName("MediaStore worker"))
fun Context.musicFilesAsFlow(): StateFlow<List<Song>> = flow {
    var version: String? = null

    while (currentCoroutineContext().isActive) {
        val newVersion = MediaStore.getVersion(applicationContext)

        if (version != newVersion) {
            version = newVersion

            AudioMediaCursor.query(contentResolver) {
                buildList {
                    while (next()) {
                        if (!isMusic || duration == 0) continue
                        add(
                            Song(
                                id = "$LOCAL_KEY_PREFIX$id",
                                title = name,
                                artistsText = artist,
                                durationText = duration.milliseconds.toComponents { minutes, seconds, _ ->
                                    "$minutes:${seconds.toString().padStart(2, '0')}"
                                },
                                thumbnailUrl = albumUri.toString()
                            )
                        )
                    }
                }
            }?.let { emit(it) }
        }
        delay(5.seconds)
    }
}.distinctUntilChanged()
    .onEach { songs -> transaction { songs.forEach(Database::insert) } }
    .stateIn(mediaScope, SharingStarted.Eagerly, listOf())
