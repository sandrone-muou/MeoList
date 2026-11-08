package com.meolist.core.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.meolist.core.R
import com.meolist.core.theme.LocalMeoLayout
import com.meolist.core.theme.Meo
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val DAY_MS = 86_400_000L

internal val timePickerMinutes: List<Int> = (0..59).toList()

internal fun timePickerHours(is24Hour: Boolean): List<Int> =
    if (is24Hour) (0..23).toList() else listOf(12) + (1..11).toList()

internal fun timePickerHourIndex(hour: Int, is24Hour: Boolean): Int =
    if (is24Hour) hour.coerceIn(0, 23) else hour.coerceIn(0, 23) % 12

internal fun timePickerHourForIndex(index: Int, is24Hour: Boolean, isPm: Boolean): Int {
    if (is24Hour) return index.coerceIn(0, 23)
    val displayHour = if (index == 0) 12 else index.coerceIn(0, 11)
    return displayHour % 12 + if (isPm) 12 else 0
}

internal fun parseTimePickerHour(text: String, is24Hour: Boolean): Int? {
    val value = text.toIntOrNull() ?: return null
    return value.takeIf { it in (if (is24Hour) 0..23 else 1..12) }
}

internal fun parseTimePickerMinute(text: String): Int? =
    text.toIntOrNull()?.takeIf { it in 0..59 }

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

@Composable
fun MeoTimePickerDialog(
    initial: LocalTime,
    onConfirm: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
    onClear: (() -> Unit)? = null,
    title: String? = null,
) {
    val is24h = android.text.format.DateFormat.is24HourFormat(LocalContext.current)
    var modeName by rememberSaveable { mutableStateOf(TimePickerMode.WHEEL.name) }
    var hour by rememberSaveable { mutableIntStateOf(initial.hour) }
    var minute by rememberSaveable { mutableIntStateOf(initial.minute) }
    var hourText by rememberSaveable {
        mutableStateOf(twoDigits(if (is24h) initial.hour else displayHour(initial.hour)))
    }
    var minuteText by rememberSaveable { mutableStateOf(twoDigits(initial.minute)) }
    var isPm by rememberSaveable { mutableStateOf(initial.hour >= 12) }
    var hourScrolling by rememberSaveable { mutableStateOf(false) }
    var minuteScrolling by rememberSaveable { mutableStateOf(false) }
    val mode = TimePickerMode.valueOf(modeName)

    val parsedHour = parseTimePickerHour(hourText, is24h)
    val parsedMinute = parseTimePickerMinute(minuteText)
    val inputValid = parsedHour != null && parsedMinute != null
    val canConfirm = if (mode == TimePickerMode.WHEEL) {
        !hourScrolling && !minuteScrolling
    } else {
        inputValid
    }

    fun setHourFromText(value: String) {
        if (value.length > 2 || value.any { !it.isDigit() }) return
        hourText = value
        parseTimePickerHour(value, is24h)?.let {
            hour = if (is24h) it else timePickerHourForIndex(it % 12, false, isPm)
        }
    }

    fun setMinuteFromText(value: String) {
        if (value.length > 2 || value.any { !it.isDigit() }) return
        minuteText = value
        parseTimePickerMinute(value)?.let { minute = it }
    }

    fun setPeriod(pm: Boolean) {
        isPm = pm
        parsedHour?.let { hour = timePickerHourForIndex(it % 12, false, pm) }
    }

    fun submit() {
        if (!canConfirm) return
        val selectedHour = if (mode == TimePickerMode.INPUT && !is24h) {
            timePickerHourForIndex(requireNotNull(parsedHour) % 12, false, isPm)
        } else if (mode == TimePickerMode.INPUT) {
            requireNotNull(parsedHour)
        } else {
            hour
        }
        val selectedMinute = if (mode == TimePickerMode.INPUT) requireNotNull(parsedMinute) else minute
        onConfirm(LocalTime.of(selectedHour, selectedMinute))
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize().imePadding().padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.widthIn(max = LocalMeoLayout.current.timeDialogMaxWidth).fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                tonalElevation = 6.dp,
            ) {
                Column {
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                    ) {
                        title?.let {
                            Text(it, style = MaterialTheme.typography.titleLarge)
                            Spacer(Modifier.height(Meo.spacing.md))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                            FilterChip(
                                selected = mode == TimePickerMode.WHEEL,
                                enabled = mode == TimePickerMode.WHEEL || inputValid,
                                onClick = {
                                    if (mode == TimePickerMode.INPUT && inputValid) {
                                        hour = timePickerHourForIndex(
                                            requireNotNull(parsedHour) % 12,
                                            false,
                                            isPm,
                                        )
                                        minute = requireNotNull(parsedMinute)
                                        modeName = TimePickerMode.WHEEL.name
                                    } else if (mode == TimePickerMode.WHEEL) {
                                        modeName = TimePickerMode.WHEEL.name
                                    }
                                },
                                label = { Text(stringResource(R.string.core_time_picker_wheel)) },
                            )
                            FilterChip(
                                selected = mode == TimePickerMode.INPUT,
                                onClick = {
                                    if (mode == TimePickerMode.WHEEL) {
                                        hourText = twoDigits(if (is24h) hour else displayHour(hour))
                                        minuteText = twoDigits(minute)
                                        modeName = TimePickerMode.INPUT.name
                                    }
                                },
                                label = { Text(stringResource(R.string.core_time_picker_input)) },
                            )
                        }
                        Spacer(Modifier.height(Meo.spacing.md))
                        if (mode == TimePickerMode.WHEEL) {
                            if (!is24h) {
                                Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                                    FilterChip(
                                        selected = !isPm,
                                        onClick = { setPeriod(false) },
                                        label = { Text(stringResource(R.string.core_time_picker_am)) },
                                    )
                                    FilterChip(
                                        selected = isPm,
                                        onClick = { setPeriod(true) },
                                        label = { Text(stringResource(R.string.core_time_picker_pm)) },
                                    )
                                }
                                Spacer(Modifier.height(Meo.spacing.sm))
                            }
                            TimeWheelRow(
                                hour = hour,
                                minute = minute,
                                is24Hour = is24h,
                                isPm = isPm,
                                onHour = {
                                    hour = it
                                    hourText = twoDigits(if (is24h) it else displayHour(it))
                                },
                                onMinute = {
                                    minute = it
                                    minuteText = twoDigits(it)
                                },
                                onHourScrolling = { hourScrolling = it },
                                onMinuteScrolling = { minuteScrolling = it },
                            )
                        } else {
                            TimeInputRow(
                                is24Hour = is24h,
                                hourText = hourText,
                                minuteText = minuteText,
                                hourInvalid = parsedHour == null,
                                minuteInvalid = parsedMinute == null,
                                isPm = isPm,
                                onPeriod = ::setPeriod,
                                onHour = ::setHourFromText,
                                onMinute = ::setMinuteFromText,
                                onDone = {
                                    if (inputValid) submit()
                                },
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        onClear?.let {
                            TextButton(onClick = {
                                it()
                                onDismiss()
                            }) {
                                Text(stringResource(R.string.core_clear))
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.core_cancel))
                        }
                        TextButton(enabled = canConfirm, onClick = ::submit) {
                            Text(stringResource(R.string.core_ok))
                        }
                    }
                }
            }
        }
    }
}

private enum class TimePickerMode { WHEEL, INPUT }

private fun twoDigits(value: Int): String = value.toString().padStart(2, '0')

private fun displayHour(hour: Int): Int = (hour % 12).let { if (it == 0) 12 else it }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimeWheelRow(
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    isPm: Boolean,
    onHour: (Int) -> Unit,
    onMinute: (Int) -> Unit,
    onHourScrolling: (Boolean) -> Unit,
    onMinuteScrolling: (Boolean) -> Unit,
) {
    val hours = if (is24Hour) timePickerHours(true) else timePickerHours(false)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm),
    ) {
        TimeWheel(
            values = hours,
            selectedIndex = timePickerHourIndex(hour, is24Hour),
            description = stringResource(R.string.core_time_picker_hour_wheel),
            onSelected = { index ->
                onHour(if (is24Hour) index else timePickerHourForIndex(index, false, isPm))
            },
            onScrollingChange = onHourScrolling,
            modifier = Modifier.weight(1f),
        )
        Text(
            ":",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        TimeWheel(
            values = timePickerMinutes,
            selectedIndex = minute.coerceIn(0, 59),
            description = stringResource(R.string.core_time_picker_minute_wheel),
            onSelected = { index -> onMinute(index) },
            onScrollingChange = onMinuteScrolling,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TimeWheel(
    values: List<Int>,
    selectedIndex: Int,
    description: String,
    onSelected: (Int) -> Unit,
    onScrollingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = LocalMeoLayout.current
    val state = rememberLazyListState(
        initialFirstVisibleItemIndex = selectedIndex.coerceIn(values.indices),
    )
    val centeredIndex by remember {
        derivedStateOf { centeredItemIndex(state.layoutInfo) }
    }
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.isScrollInProgress) {
        onScrollingChange(state.isScrollInProgress)
    }
    LaunchedEffect(centeredIndex, state.isScrollInProgress) {
        if (!state.isScrollInProgress) {
            centeredIndex?.let { index -> values.getOrNull(index)?.let(onSelected) }
        }
    }
    LaunchedEffect(selectedIndex) {
        if (!state.isScrollInProgress && centeredItemIndex(state.layoutInfo) != selectedIndex) {
            state.scrollToItem(selectedIndex.coerceIn(values.indices))
        }
    }

    Box(
        modifier = modifier
            .height(layout.timeWheelHeight)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(layout.timeWheelRowHeight)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.shapes.medium,
                ),
        )
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = layout.timeWheelRowHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(values.size, key = { values[it] }) { index ->
                val value = values[index]
                val centered = centeredIndex == index
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(layout.timeWheelRowHeight)
                        .clickable {
                            scope.launch { state.animateScrollToItem(index) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = twoDigits(value),
                        style = if (centered) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        fontWeight = if (centered) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (centered) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        },
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

private fun centeredItemIndex(info: LazyListLayoutInfo): Int? {
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2f
    return info.visibleItemsInfo.minByOrNull { item ->
        abs(item.offset + item.size / 2f - center)
    }?.index
}

@Composable
private fun TimeInputRow(
    is24Hour: Boolean,
    hourText: String,
    minuteText: String,
    hourInvalid: Boolean,
    minuteInvalid: Boolean,
    isPm: Boolean,
    onPeriod: (Boolean) -> Unit,
    onHour: (String) -> Unit,
    onMinute: (String) -> Unit,
    onDone: () -> Unit,
) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    Column {
        if (!is24Hour) {
            Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
                FilterChip(
                    selected = !isPm,
                    onClick = { onPeriod(false) },
                    label = { Text(stringResource(R.string.core_time_picker_am)) },
                )
                FilterChip(
                    selected = isPm,
                    onClick = { onPeriod(true) },
                    label = { Text(stringResource(R.string.core_time_picker_pm)) },
                )
            }
            Spacer(Modifier.height(Meo.spacing.sm))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Meo.spacing.sm)) {
            OutlinedTextField(
                value = hourText,
                onValueChange = onHour,
                label = { Text(stringResource(R.string.core_time_picker_hour)) },
                isError = hourInvalid,
                supportingText = if (hourInvalid) {
                    { Text(stringResource(R.string.core_time_picker_invalid_hour)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focus.moveFocus(FocusDirection.Next) },
                    onDone = {
                        keyboard?.hide()
                        onDone()
                    },
                ),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = minuteText,
                onValueChange = onMinute,
                label = { Text(stringResource(R.string.core_time_picker_minute)) },
                isError = minuteInvalid,
                supportingText = if (minuteInvalid) {
                    { Text(stringResource(R.string.core_time_picker_invalid_minute)) }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        keyboard?.hide()
                        onDone()
                    },
                ),
                modifier = Modifier.weight(1f),
            )
        }
    }
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
