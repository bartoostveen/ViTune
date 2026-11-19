package app.vitune.android.ui.components.themed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.text.BasicSecureTextField
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import app.vitune.android.utils.secondary
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.Appearance
import app.vitune.core.ui.LocalAppearance
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged

// TODO: refactor all usages to new state-based text fields instead of value-based
// TODO: deduplicate code

@Composable
fun ColumnScope.TextField(
    initialText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputTranformation: InputTransformation? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = if (lineLimits == TextFieldLineLimits.SingleLine) ImeAction.Done else ImeAction.None
    ),
    outputTransformation: OutputTransformation? = null,
    onTextLayout: (Density.(() -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null,
) {
    val state = rememberTextFieldState(initialText)

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .collect { onValueChange(it.toString()) }
    }

    BasicTextField(
        state = state,
        inputTransformation = inputTranformation,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = textStyle,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(appearance.colorPalette.text),
        outputTransformation = outputTransformation,
        lineLimits = lineLimits,
        decorator = TextFieldDecorator { innerTextField ->
            hintText?.let { text ->
                this@TextField.AnimatedVisibility(
                    visible = initialText.isEmpty(),
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle.secondary
                    )
                }
            }

            innerTextField()
        }
    )
}

@Composable
fun RowScope.TextField(
    initialText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputTranformation: InputTransformation? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onKeyboardAction: KeyboardActionHandler? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = if (lineLimits == TextFieldLineLimits.SingleLine) ImeAction.Done else ImeAction.None
    ),
    outputTransformation: OutputTransformation? = null,
    onTextLayout: (Density.(() -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null,
) {
    val state = rememberTextFieldState(initialText)

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .collect { onValueChange(it.toString()) }
    }

    BasicTextField(
        state = state,
        inputTransformation = inputTranformation,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = textStyle,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(appearance.colorPalette.text),
        outputTransformation = outputTransformation,
        lineLimits = lineLimits,
        decorator = TextFieldDecorator { innerTextField ->
            hintText?.let { text ->
                this@TextField.AnimatedVisibility(
                    visible = initialText.isEmpty(),
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle.secondary
                    )
                }
            }

            innerTextField()
        }
    )
}

@Composable
fun ColumnScope.SecureTextField(
    initialText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputTranformation: InputTransformation? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    onKeyboardAction: KeyboardActionHandler? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onTextLayout: (Density.(() -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null,
) {
    val state = rememberTextFieldState(initialText)

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .collect { onValueChange(it.toString()) }
    }

    BasicSecureTextField(
        state = state,
        inputTransformation = inputTranformation,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = textStyle,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(appearance.colorPalette.text),
        decorator = TextFieldDecorator { innerTextField ->
            hintText?.let { text ->
                this@SecureTextField.AnimatedVisibility(
                    visible = initialText.isEmpty(),
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle.secondary
                    )
                }
            }

            innerTextField()
        }
    )
}

@Composable
fun RowScope.SecureTextField(
    initialText: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    inputTranformation: InputTransformation? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    appearance: Appearance = LocalAppearance.current,
    textStyle: TextStyle = appearance.typography.xs.semiBold,
    onKeyboardAction: KeyboardActionHandler? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    onTextLayout: (Density.(() -> TextLayoutResult?) -> Unit)? = null,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hintText: String? = null,
) {
    val state = rememberTextFieldState(initialText)

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .collect { onValueChange(it.toString()) }
    }

    BasicSecureTextField(
        state = state,
        inputTransformation = inputTranformation,
        modifier = modifier,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        onKeyboardAction = onKeyboardAction,
        textStyle = textStyle,
        onTextLayout = onTextLayout,
        interactionSource = interactionSource,
        cursorBrush = SolidColor(appearance.colorPalette.text),
        decorator = TextFieldDecorator { innerTextField ->
            hintText?.let { text ->
                this@SecureTextField.AnimatedVisibility(
                    visible = initialText.isEmpty(),
                    enter = fadeIn(tween(100)),
                    exit = fadeOut(tween(100)),
                    modifier = Modifier.weight(1f)
                ) {
                    BasicText(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = textStyle.secondary
                    )
                }
            }

            innerTextField()
        }
    )
}
