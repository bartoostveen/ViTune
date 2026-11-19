package app.vitune.android.ui.components.themed

import androidx.annotation.IntRange
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.vitune.android.R
import app.vitune.android.utils.center
import app.vitune.android.utils.drawCircle
import app.vitune.android.utils.medium
import app.vitune.android.utils.semiBold
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.roundedShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun TextFieldDialog(
    hintText: String,
    onDismiss: () -> Unit,
    onAccept: (String) -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    initialTextInput: String = "",
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.SingleLine,
    onCancel: () -> Unit = onDismiss,
    isTextInputValid: (String) -> Boolean = { it.isNotEmpty() },
    keyboardOptions: KeyboardOptions = KeyboardOptions(
        imeAction = if (lineLimits == TextFieldLineLimits.SingleLine) ImeAction.Done else ImeAction.None
    )
) = DefaultDialog(
    onDismiss = onDismiss,
    modifier = modifier
) {
    val focusRequester = remember { FocusRequester() }
    val [_, typography] = LocalAppearance.current

    var value by rememberSaveable(initialTextInput) { mutableStateOf(initialTextInput) }

    LaunchedEffect(Unit) {
        delay(300.milliseconds)
        focusRequester.requestFocus()
    }

    TextField(
        initialText = value,
        onValueChange = { value = it },
        modifier = Modifier
            .padding(all = 16.dp)
            .weight(weight = 1f, fill = false)
            .focusRequester(focusRequester),
        textStyle = typography.xs.semiBold.center,
        lineLimits = lineLimits,
        onKeyboardAction = { performDefaultAction ->
            if (!isTextInputValid(value)) {
                return@TextField
            }

            onDismiss()
            onAccept(value)
            performDefaultAction()
        },
        keyboardOptions = keyboardOptions,
        hintText = hintText,
    )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        DialogTextButton(
            text = cancelText,
            onClick = onCancel
        )

        DialogTextButton(
            primary = true,
            text = doneText,
            onClick = {
                if (isTextInputValid(value)) {
                    onDismiss()
                    onAccept(value)
                }
            }
        )
    }
}

@Composable
fun <T> NumberFieldDialog(
    onDismiss: () -> Unit,
    onAccept: (T) -> Unit,
    initialValue: T,
    defaultValue: T,
    convert: (String) -> T?,
    range: ClosedRange<T>,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    doneText: String = stringResource(R.string.done),
    onCancel: () -> Unit = onDismiss
) where T : Number, T : Comparable<T> = TextFieldDialog(
    hintText = "",
    onDismiss = onDismiss,
    onAccept = { onAccept((convert(it) ?: defaultValue).coerceIn(range)) },
    modifier = modifier,
    cancelText = cancelText,
    doneText = doneText,
    initialTextInput = initialValue.toString(),
    onCancel = onCancel,
    isTextInputValid = { true },
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
)

@Composable
fun ConfirmationDialog(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss
) = DefaultDialog(
    onDismiss = onDismiss,
    modifier = modifier
) {
    ConfirmationDialogBody(
        text = text,
        onDismiss = onDismiss,
        onConfirm = onConfirm,
        cancelText = cancelText,
        confirmText = confirmText,
        onCancel = onCancel
    )
}

@Suppress("ModifierMissing", "UnusedReceiverParameter")
@Composable
fun ColumnScope.ConfirmationDialogBody(
    text: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    cancelText: String = stringResource(R.string.cancel),
    confirmText: String = stringResource(R.string.confirm),
    onCancel: () -> Unit = onDismiss
) {
    val [_, typography] = LocalAppearance.current

    BasicText(
        text = text,
        style = typography.xs.medium.center,
        modifier = Modifier.padding(all = 16.dp)
    )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier.fillMaxWidth()
    ) {
        DialogTextButton(
            text = cancelText,
            onClick = {
                onCancel()
                onDismiss()
            }
        )

        DialogTextButton(
            text = confirmText,
            primary = true,
            onClick = {
                onConfirm()
                onDismiss()
            }
        )
    }
}

@Composable
fun DefaultDialog(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    horizontalAlignment: Alignment.Horizontal = Alignment.CenterHorizontally,
    horizontalPadding: Dp = 24.dp,
    content: @Composable ColumnScope.() -> Unit
) = Dialog(onDismissRequest = onDismiss) {
    Column(
        horizontalAlignment = horizontalAlignment,
        modifier = modifier
            .padding(all = 48.dp)
            .background(
                color = LocalAppearance.current.colorPalette.background1,
                shape = 8.dp.roundedShape
            )
            .padding(
                horizontal = horizontalPadding,
                vertical = 16.dp
            ),
        content = content
    )
}

@Composable
fun <T> ValueSelectorDialog(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: T,
    values: ImmutableList<T>,
    onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueText: @Composable (T) -> String = { it.toString() }
) = Dialog(onDismissRequest = onDismiss) {
    ValueSelectorDialogBody(
        onDismiss = onDismiss,
        title = title,
        selectedValue = selectedValue,
        values = values,
        onValueSelect = onValueSelect,
        modifier = modifier
            .padding(all = 48.dp)
            .background(
                color = LocalAppearance.current.colorPalette.background1,
                shape = 8.dp.roundedShape
            )
            .padding(vertical = 16.dp),
        valueText = valueText
    )
}

@Composable
fun <T> ValueSelectorDialogBody(
    onDismiss: () -> Unit,
    title: String,
    selectedValue: T?,
    values: ImmutableList<T>,
    onValueSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    valueText: @Composable (T) -> String = { it.toString() }
) = Column(modifier = modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    BasicText(
        text = title,
        style = typography.s.semiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
    )

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        values.forEach { value ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .clickable(
                        onClick = {
                            onDismiss()
                            onValueSelect(value)
                        }
                    )
                    .padding(vertical = 12.dp, horizontal = 24.dp)
                    .fillMaxWidth()
            ) {
                if (selectedValue == value) Canvas(
                    modifier = Modifier
                        .size(18.dp)
                        .background(
                            color = colorPalette.accent,
                            shape = CircleShape
                        )
                ) {
                    drawCircle(
                        color = colorPalette.onAccent,
                        radius = 4.dp.toPx(),
                        center = size.center,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.4f),
                            blurRadius = 4.dp.toPx(),
                            offset = Offset(x = 0f, y = 1.dp.toPx())
                        )
                    )
                } else Spacer(
                    modifier = Modifier
                        .size(18.dp)
                        .border(
                            width = 1.dp,
                            color = colorPalette.textDisabled,
                            shape = CircleShape
                        )
                )

                BasicText(
                    text = valueText(value),
                    style = typography.xs.medium
                )
            }
        }
    }

    Box(
        modifier = Modifier
            .align(Alignment.End)
            .padding(end = 24.dp)
    ) {
        DialogTextButton(
            text = stringResource(R.string.cancel),
            onClick = onDismiss
        )
    }
}

@Suppress("ModifierMissing") // intentional, I guess
@Composable
fun ColumnScope.SliderDialogBody(
    provideState: @Composable () -> MutableState<Float>,
    onSlideComplete: (newState: Float) -> Unit,
    min: Float,
    max: Float,
    toDisplay: @Composable (Float) -> String = { it.toString() },
    @IntRange(from = 0) steps: Int = 0,
    label: String? = null
) {
    val [_, typography] = LocalAppearance.current
    var state by provideState()

    if (label != null) BasicText(
        text = label,
        style = typography.xs.semiBold,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
    )

    Slider(
        state = state,
        setState = { state = it },
        onSlideComplete = { onSlideComplete(state) },
        range = min..max,
        steps = steps,
        modifier = Modifier
            .height(36.dp)
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    )

    BasicText(
        text = toDisplay(state),
        style = typography.s.semiBold,
        modifier = Modifier
            .align(Alignment.CenterHorizontally)
            .padding(vertical = 8.dp)
    )
}

@Composable
fun SliderDialog(
    onDismiss: () -> Unit,
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = { }
) = Dialog(onDismissRequest = onDismiss) {
    val (colorPalette, typography) = LocalAppearance.current

    Column(
        modifier = modifier
            .padding(all = 48.dp)
            .background(color = colorPalette.background1, shape = 8.dp.roundedShape)
            .padding(vertical = 16.dp)
    ) {
        BasicText(
            text = title,
            style = typography.s.semiBold,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 24.dp)
        )

        content()

        Box(
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 24.dp)
        ) {
            DialogTextButton(
                text = stringResource(R.string.confirm),
                onClick = onDismiss,
                modifier = Modifier
            )
        }
    }
}
