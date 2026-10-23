package com.meolist.core.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.meolist.core.R
import java.time.LocalDate
import java.time.LocalTime

private const val DAY_MS = 86_400_000L

/** Material date picker working in [LocalDate]s (the picker itself uses UTC midnight millis). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeoDatePickerDialog(
    initial: LocalDate?,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial?.let { it.toEpochDay() * DAY_MS })
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onConfirm(LocalDate.ofEpochDay(Math.floorDiv(it, DAY_MS))) }
                    onDismiss()
                },
                enabled = state.selectedDateMillis != null,
            ) { Text(stringResource(R.string.core_ok)) }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.core_clear)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_cancel)) }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeoTimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
) {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    val state = rememberTimePickerState(initial.hour, initial.minute, is24h)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(LocalTime.of(state.hour, state.minute)); onDismiss() }) {
                Text(stringResource(R.string.core_ok))
            }
        },
        dismissButton = {
            if (onClear != null) {
                TextButton(onClick = { onClear(); onDismiss() }) { Text(stringResource(R.string.core_clear)) }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_cancel)) }
            }
        },
        text = { TimePicker(state = state) },
    )
}

/** Simple confirmation dialog for destructive actions. */
@Composable
fun ConfirmDialog(
    title: String,
    text: String?,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = text?.let { { Text(it) } },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.core_cancel)) } },
    )
}
