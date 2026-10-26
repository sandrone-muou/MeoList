package com.meolist.core.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.meolist.core.R

/** Handle returned by [rememberBackGuard]; call [request] from the top bar's back arrow. */
class BackGuard(val request: () -> Unit)

/**
 * Intercepts both the system back gesture and the top bar arrow while there are unsaved edits.
 * Discarding runs [onBack]; cancelling keeps the screen and the edits.
 */
@Composable
fun rememberBackGuard(dirty: Boolean, onBack: () -> Unit): BackGuard {
    var asking by remember { mutableStateOf(false) }
    BackHandler(enabled = dirty) { asking = true }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text(stringResource(R.string.core_discard_title)) },
            text = { Text(stringResource(R.string.core_discard_text)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        asking = false
                        onBack()
                    },
                ) {
                    Text(stringResource(R.string.core_discard))
                }
            },
            dismissButton = {
                TextButton(onClick = { asking = false }) {
                    Text(stringResource(R.string.core_cancel))
                }
            },
        )
    }
    return BackGuard { if (dirty) asking = true else onBack() }
}
