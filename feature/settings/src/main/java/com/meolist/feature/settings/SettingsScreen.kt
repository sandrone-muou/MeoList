package com.meolist.feature.settings

import android.net.Uri
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PriorityHigh
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Timelapse
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meolist.core.ui.ConfirmDialog
import com.meolist.core.ui.ReminderPermissionBanner
import com.meolist.core.ui.currentLocale
import com.meolist.core.ui.rememberReminderPermissionState
import com.meolist.core.ui.timeLabel
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.TextStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissions = rememberReminderPermissionState()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    var choosingIgnore by remember { mutableStateOf(false) }
    var choosingUrgent by remember { mutableStateOf(false) }
    var choosingWeekStart by remember { mutableStateOf(false) }
    var choosingReminderTime by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(vm::writeBackup)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pendingRestore = uri
    }
    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(vm::exportCsv)
    }

    LaunchedEffect(state.event) {
        val event = state.event ?: return@LaunchedEffect
        val message = when (event) {
            SettingsEvent.BackupSaved -> context.getString(R.string.settings_backup_done)
            SettingsEvent.CsvSaved -> context.getString(R.string.settings_csv_done)
            is SettingsEvent.Restored -> context.getString(
                R.string.settings_restore_done,
                context.resources.getQuantityString(
                    R.plurals.settings_restore_tasks,
                    event.summary.tasks,
                    event.summary.tasks,
                ),
                context.resources.getQuantityString(
                    R.plurals.settings_restore_records,
                    event.summary.records,
                    event.summary.records,
                ),
                context.resources.getQuantityString(
                    R.plurals.settings_restore_habits,
                    event.summary.habits,
                    event.summary.habits,
                ),
            )
            SettingsEvent.InvalidBackup -> context.getString(R.string.settings_restore_invalid)
            SettingsEvent.Failed -> context.getString(R.string.settings_action_failed)
        }
        snackbar.showSnackbar(message)
        vm.consumeEvent()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(com.meolist.core.R.string.core_back))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            if (state.busy) {
                item {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().clip(CircleShape),
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_timing)) }
            item {
                SettingsCard {
                    SwitchRow(
                        icon = Icons.Outlined.Timer,
                        title = stringResource(R.string.settings_multiple_timers),
                        subtitle = stringResource(R.string.settings_multiple_timers_desc),
                        checked = settings.multipleTimers,
                        onChange = vm::setMultipleTimers,
                    )
                    RowDivider()
                    ChoiceRow(
                        icon = Icons.Outlined.Timelapse,
                        title = stringResource(R.string.settings_ignore_short),
                        subtitle = stringResource(R.string.settings_ignore_short_desc),
                        value = if (settings.ignoreShortRecordSec == 0) {
                            stringResource(R.string.settings_ignore_none)
                        } else {
                            stringResource(R.string.settings_ignore_seconds, settings.ignoreShortRecordSec)
                        },
                        onClick = { choosingIgnore = true },
                    )
                    RowDivider()
                    SwitchRow(
                        icon = Icons.Outlined.StopCircle,
                        title = stringResource(R.string.settings_stop_on_complete),
                        subtitle = stringResource(R.string.settings_stop_on_complete_desc),
                        checked = settings.stopTimerOnTaskComplete,
                        onChange = vm::setStopOnComplete,
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_tasks)) }
            item {
                SettingsCard {
                    ChoiceRow(
                        icon = Icons.Outlined.PriorityHigh,
                        title = stringResource(R.string.settings_urgent_days),
                        subtitle = stringResource(R.string.settings_urgent_days_desc),
                        value = stringResource(R.string.settings_urgent_value, settings.urgentDays),
                        onClick = { choosingUrgent = true },
                    )
                    RowDivider()
                    ChoiceRow(
                        icon = Icons.Outlined.DateRange,
                        title = stringResource(R.string.settings_week_start),
                        subtitle = null,
                        value = settings.firstDayOfWeek.getDisplayName(TextStyle.FULL, currentLocale()),
                        onClick = { choosingWeekStart = true },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_reminders)) }
            if (!permissions.notificationsEnabled || !permissions.exactAlarmsAllowed) {
                item { ReminderPermissionBanner(permissions) }
            }
            item {
                SettingsCard {
                    ChoiceRow(
                        icon = Icons.Outlined.Notifications,
                        title = stringResource(R.string.settings_default_reminder),
                        subtitle = stringResource(R.string.settings_default_reminder_desc),
                        value = timeLabel(
                            LocalTime.of(settings.defaultReminderMinute / 60, settings.defaultReminderMinute % 60),
                        ),
                        onClick = { choosingReminderTime = true },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_data)) }
            item {
                SettingsCard {
                    ActionRow(
                        icon = Icons.Outlined.CloudUpload,
                        title = stringResource(R.string.settings_backup),
                        subtitle = stringResource(R.string.settings_backup_desc),
                        enabled = !state.busy,
                        onClick = { backupLauncher.launch("meolist-backup-${LocalDate.now()}.json") },
                    )
                    RowDivider()
                    ActionRow(
                        icon = Icons.Outlined.SettingsBackupRestore,
                        title = stringResource(R.string.settings_restore),
                        subtitle = stringResource(R.string.settings_restore_desc),
                        enabled = !state.busy,
                        onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                    )
                    RowDivider()
                    ActionRow(
                        icon = Icons.Outlined.Description,
                        title = stringResource(R.string.settings_export_csv),
                        subtitle = stringResource(R.string.settings_export_csv_desc),
                        enabled = !state.busy,
                        onClick = { csvLauncher.launch("meolist-records-${LocalDate.now()}.csv") },
                    )
                }
            }

            item { SectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                AboutCard()
            }
        }
    }

    if (choosingIgnore) {
        ChoiceDialog(
            title = stringResource(R.string.settings_ignore_short),
            options = listOf(0, 30, 60, 120, 300).map { seconds ->
                seconds to if (seconds == 0) {
                    stringResource(R.string.settings_ignore_none)
                } else {
                    stringResource(R.string.settings_ignore_seconds, seconds)
                }
            },
            selected = settings.ignoreShortRecordSec,
            onSelect = { vm.setIgnoreShortSeconds(it); choosingIgnore = false },
            onDismiss = { choosingIgnore = false },
        )
    }
    if (choosingUrgent) {
        ChoiceDialog(
            title = stringResource(R.string.settings_urgent_days),
            options = listOf(1, 2, 3, 7, 14).map { days ->
                days to stringResource(R.string.settings_urgent_value, days)
            },
            selected = settings.urgentDays,
            onSelect = { vm.setUrgentDays(it); choosingUrgent = false },
            onDismiss = { choosingUrgent = false },
        )
    }
    if (choosingWeekStart) {
        val locale = currentLocale()
        ChoiceDialog(
            title = stringResource(R.string.settings_week_start),
            options = DayOfWeek.entries.map { it to it.getDisplayName(TextStyle.FULL, locale) },
            selected = settings.firstDayOfWeek,
            onSelect = { vm.setFirstDayOfWeek(it); choosingWeekStart = false },
            onDismiss = { choosingWeekStart = false },
        )
    }
    if (choosingReminderTime) {
        ReminderTimeDialog(
            initialMinute = settings.defaultReminderMinute,
            onConfirm = { vm.setDefaultReminderMinute(it); choosingReminderTime = false },
            onDismiss = { choosingReminderTime = false },
        )
    }

    pendingRestore?.let { uri ->
        ConfirmDialog(
            title = stringResource(R.string.settings_restore_confirm_title),
            text = stringResource(R.string.settings_restore_confirm_text),
            confirmLabel = stringResource(R.string.settings_restore_action),
            onConfirm = { vm.restore(uri) },
            onDismiss = { pendingRestore = null },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(content = { content() })
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        leadingContent = { LeadingIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun ChoiceRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    value: String,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { LeadingIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis) } },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Icon(
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        leadingContent = { LeadingIcon(icon) },
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun LeadingIcon(icon: ImageVector) {
    Row(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun AboutCard() {
    val uriHandler = LocalUriHandler.current
    val version = stringResource(R.string.settings_app_version)
    SettingsCard {
        ListItem(
            leadingContent = { LeadingIcon(Icons.Outlined.Info) },
            headlineContent = { Text(stringResource(R.string.settings_about_version, version)) },
            supportingContent = { Text(stringResource(R.string.settings_about_license)) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        RowDivider()
        ListItem(
            leadingContent = { LeadingIcon(Icons.Outlined.Person) },
            headlineContent = { Text(stringResource(R.string.settings_about_author)) },
            supportingContent = { Text("github.com/sandrone-muou") },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            modifier = Modifier.clickable {
                runCatching { uriHandler.openUri("https://github.com/sandrone-muou") }
            },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { onSelect(value) }
                            .padding(vertical = 2.dp),
                    ) {
                        RadioButton(selected = value == selected, onClick = { onSelect(value) })
                        Text(label,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(com.meolist.core.R.string.core_cancel)) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimeDialog(
    initialMinute: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberTimePickerState(
        initialHour = initialMinute / 60,
        initialMinute = initialMinute % 60,
        is24Hour = DateFormat.is24HourFormat(context),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(
                    stringResource(R.string.settings_default_reminder),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Spacer(Modifier.height(16.dp))
                TimePicker(state = picker)
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(com.meolist.core.R.string.core_cancel))
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { onConfirm(picker.hour * 60 + picker.minute) }) {
                        Text(stringResource(com.meolist.core.R.string.core_ok))
                    }
                }
            }
        }
    }
}
