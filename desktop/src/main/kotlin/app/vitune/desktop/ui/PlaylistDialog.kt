package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
private fun DialogCard(onDismiss: () -> Unit, width: Int = 420, content: @Composable ColumnScope.() -> Unit) {
    val ui = LocalUi.current
    // Keep single-key player shortcuts from firing while a dialog is open
    DisposableEffect(Unit) {
        ui.typing = true
        onDispose { ui.typing = false }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            color = Palette.PanelRaised,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 24.dp,
            modifier = Modifier.width(width.dp)
        ) {
            Column(Modifier.padding(24.dp)) { content() }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Palette.Violet,
    unfocusedBorderColor = Palette.Outline,
    focusedLabelColor = Palette.Violet,
    cursorColor = Palette.Violet,
    focusedContainerColor = Color.White.copy(alpha = 0.03f),
    unfocusedContainerColor = Color.White.copy(alpha = 0.03f)
)

@Composable
fun PlaylistEditorDialog(
    title: String,
    initialName: String = "",
    initialDescription: String = "",
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, description: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    DialogCard(onDismiss) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(80) },
            label = { Text("Name") },
            placeholder = { Text("My playlist") },
            singleLine = true,
            colors = fieldColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it.take(300) },
            label = { Text("Description (optional)") },
            maxLines = 3,
            colors = fieldColors(),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
            PillButton("Cancel", onClick = onDismiss)
            PillButton(confirmLabel, filled = true, onClick = {
                if (name.isNotBlank()) onConfirm(name.trim(), description.trim())
            })
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    DialogCard(onDismiss, width = 380) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
        Spacer(Modifier.height(10.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
            PillButton("Cancel", onClick = onDismiss)
            PillButton(confirmLabel, filled = true, onClick = onConfirm)
        }
    }
}

private val Shortcuts = listOf(
    "Space" to "Play / pause",
    "Ctrl + P" to "Play / pause",
    "Ctrl + →  /  Ctrl + ←" to "Next / previous song",
    "Shift + →  /  Shift + ←" to "Seek forward / back 10 seconds",
    "Ctrl + ↑  /  Ctrl + ↓" to "Volume up / down",
    "M" to "Mute / unmute",
    "Ctrl + L" to "Like the current song",
    "Ctrl + S" to "Shuffle on / off",
    "Ctrl + R" to "Cycle repeat mode",
    "Ctrl + F  or  Ctrl + K" to "Search",
    "F" to "Open / close Now Playing",
    "Esc" to "Close Now Playing",
    "Alt + ←" to "Go back"
)

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    DialogCard(onDismiss, width = 460) {
        Text("Keyboard shortcuts", style = MaterialTheme.typography.headlineSmall, color = Palette.TextPrimary)
        Spacer(Modifier.height(16.dp))
        Shortcuts.forEach { (keys, action) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(action, style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary, modifier = Modifier.weight(1f))
                Text(
                    keys,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        PillButton("Got it", filled = true, onClick = onDismiss, modifier = Modifier.align(Alignment.End))
    }
}
