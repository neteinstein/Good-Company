package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.save
import org.jetbrains.compose.resources.stringResource

/**
 * The top of a form sheet: its title and a Save that stays in reach above the scrolling fields (decision "Pickers ·
 * save without a Save button", issue #64). Such sheets also save when closed while valid; put this outside their
 * `verticalScroll`.
 */
@Composable
fun SaveSheetHeader(title: String, onSave: () -> Unit, modifier: Modifier = Modifier, saveEnabled: Boolean = true) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, Modifier.weight(1f).padding(end = 12.dp), style = MaterialTheme.typography.titleLarge)
        Button(onClick = onSave, enabled = saveEnabled) { Text(stringResource(Res.string.save)) }
    }
}
