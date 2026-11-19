package app.vitune.android.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.password
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.credentials.CredentialManager
import app.vitune.android.Database
import app.vitune.android.LocalCredentialManager
import app.vitune.android.R
import app.vitune.android.models.PipedSession
import app.vitune.android.transaction
import app.vitune.android.ui.components.themed.CircularProgressIndicator
import app.vitune.android.ui.components.themed.ConfirmationDialog
import app.vitune.android.ui.components.themed.ConfirmationDialogBody
import app.vitune.android.ui.components.themed.DefaultDialog
import app.vitune.android.ui.components.themed.DialogTextButton
import app.vitune.android.ui.components.themed.IconButton
import app.vitune.android.ui.components.themed.SecureTextField
import app.vitune.android.ui.components.themed.TextField
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.center
import app.vitune.android.utils.get
import app.vitune.android.utils.semiBold
import app.vitune.android.utils.upsert
import app.vitune.compose.persist.persistList
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.piped.Piped
import app.vitune.providers.piped.models.Instance
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch

@Route
@Composable
fun SyncSettings(
    credentialManager: CredentialManager = LocalCredentialManager.current
) {
    val coroutineScope = rememberCoroutineScope()

    val (colorPalette, typography) = LocalAppearance.current
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current

    val pipedSessions by Database.pipedSessions().collectAsState(initial = listOf())

    var linkingPiped by remember { mutableStateOf(false) }
    if (linkingPiped) DefaultDialog(
        onDismiss = { linkingPiped = false },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var isLoading by rememberSaveable { mutableStateOf(false) }
        var hasError by rememberSaveable { mutableStateOf(false) }
        var successful by remember { mutableStateOf(false) }

        when {
            successful -> BasicText(
                text = stringResource(R.string.piped_session_created_successfully),
                style = typography.xs.semiBold.center,
                modifier = Modifier.padding(all = 24.dp)
            )

            hasError -> ConfirmationDialogBody(
                text = stringResource(R.string.error_piped_link),
                onDismiss = { },
                onCancel = { linkingPiped = false },
                onConfirm = { hasError = false }
            )

            isLoading -> CircularProgressIndicator(modifier = Modifier.padding(all = 8.dp))

            else -> Box(modifier = Modifier.fillMaxWidth()) {
                var backgroundLoading by rememberSaveable { mutableStateOf(false) }
                if (backgroundLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.TopEnd))

                Column(modifier = Modifier.fillMaxWidth()) {
                    var instances by persistList<Instance>(tag = "settings/sync/piped/instances")
                    var loadingInstances by rememberSaveable { mutableStateOf(true) }
                    var selectedInstance: Int? by rememberSaveable { mutableStateOf(null) }
                    var username by rememberSaveable { mutableStateOf("") }
                    var password by rememberSaveable { mutableStateOf("") }
                    var canSelect by rememberSaveable { mutableStateOf(false) }
                    var instancesUnavailable by rememberSaveable { mutableStateOf(false) }
                    var customInstance: String? by rememberSaveable { mutableStateOf(null) }

                    LaunchedEffect(Unit) {
                        Piped.getInstances()?.getOrNull()?.let {
                            selectedInstance = null
                            instances = it.toImmutableList()
                            canSelect = true
                        } ?: run { instancesUnavailable = true }
                        loadingInstances = false

                        backgroundLoading = true
                        runCatching {
                            credentialManager.get(context)?.let {
                                username = it.id
                                password = it.password
                            }
                        }.getOrNull()
                        backgroundLoading = false
                    }

                    BasicText(
                        text = stringResource(R.string.piped),
                        style = typography.m.semiBold
                    )

                    if (customInstance == null) ValueSelectorSettingsEntry(
                        title = stringResource(R.string.instance),
                        selectedValue = selectedInstance,
                        values = instances.indices.toImmutableList(),
                        onValueSelect = { selectedInstance = it },
                        valueText = { idx ->
                            idx?.let { instances.getOrNull(it)?.name }
                                ?: if (instancesUnavailable) stringResource(R.string.error_piped_instances_unavailable)
                                else stringResource(R.string.click_to_select)
                        },
                        isEnabled = !instancesUnavailable && canSelect,
                        usePadding = false,
                        trailingContent = if (loadingInstances) {
                            { CircularProgressIndicator() }
                        } else null
                    )
                    SwitchSettingsEntry(
                        title = stringResource(R.string.custom_instance),
                        text = null,
                        isChecked = customInstance != null,
                        onCheckedChange = {
                            customInstance = if (customInstance == null) "" else null
                        },
                        usePadding = false
                    )
                    customInstance?.let { instance ->
                        Spacer(modifier = Modifier.height(8.dp))
                        TextField(
                            initialText = instance,
                            onValueChange = { customInstance = it },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            hintText = stringResource(R.string.base_api_url),
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextField(
                        initialText = username,
                        onValueChange = { username = it },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        hintText = stringResource(R.string.username),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SecureTextField(
                        initialText = password,
                        onValueChange = { password = it },
                        hintText = stringResource(R.string.password),
                        keyboardOptions = KeyboardOptions(
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    DialogTextButton(
                        text = stringResource(R.string.login),
                        primary = true,
                        enabled = (customInstance?.isNotBlank() == true || selectedInstance != null) &&
                            username.isNotBlank() && password.isNotBlank(),
                        onClick = {
                            @Suppress("Wrapping") // thank you ktlint
                            (customInstance?.let {
                                runCatching {
                                    Url(it)
                                }.getOrNull() ?: runCatching {
                                    Url("https://$it")
                                }.getOrNull()
                            } ?: selectedInstance?.let { instances[it].apiBaseUrl })?.let { url ->
                                coroutineScope.launch {
                                    isLoading = true
                                    val session = Piped.login(
                                        apiBaseUrl = url,
                                        username = username,
                                        password = password
                                    )?.getOrNull()
                                    isLoading = false
                                    if (session == null) {
                                        hasError = true
                                        return@launch
                                    }

                                    transaction {
                                        Database.insert(
                                            PipedSession(
                                                apiBaseUrl = session.apiBaseUrl,
                                                username = username,
                                                token = session.token
                                            )
                                        )
                                    }

                                    successful = true

                                    runCatching {
                                        credentialManager.upsert(
                                            context = context,
                                            username = username,
                                            password = password
                                        )
                                    }

                                    linkingPiped = false
                                }
                            }
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    var deletingPipedSession: Int? by rememberSaveable { mutableStateOf(null) }
    if (deletingPipedSession != null) ConfirmationDialog(
        text = stringResource(R.string.confirm_delete_piped_session),
        onDismiss = {
            deletingPipedSession = null
        },
        onConfirm = {
            deletingPipedSession?.let {
                transaction { Database.delete(pipedSessions[it]) }
            }
        }
    )

    SettingsCategoryScreen(title = stringResource(R.string.sync)) {
        SettingsDescription(text = stringResource(R.string.sync_description))

        SettingsGroup(title = stringResource(R.string.piped)) {
            SettingsEntry(
                title = stringResource(R.string.add_account),
                text = stringResource(R.string.add_account_description),
                onClick = { linkingPiped = true }
            )
            SettingsEntry(
                title = stringResource(R.string.learn_more),
                text = stringResource(R.string.learn_more_description),
                onClick = { uriHandler.openUri("https://github.com/TeamPiped/Piped/blob/master/README.md") }
            )
        }
        SettingsGroup(title = stringResource(R.string.piped_sessions)) {
            if (pipedSessions.isEmpty()) {
                SettingsGroupSpacer()

                BasicText(
                    text = stringResource(R.string.no_items_found),
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = typography.s.semiBold.center
                )
            } else pipedSessions.fastForEachIndexed { i, session ->
                SettingsEntry(
                    title = session.username,
                    text = session.apiBaseUrl.toString(),
                    onClick = { },
                    trailingContent = {
                        IconButton(
                            onClick = { deletingPipedSession = i },
                            icon = R.drawable.delete,
                            color = colorPalette.text
                        )
                    }
                )
            }
        }
    }
}
