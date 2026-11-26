package org.goodcompany.app.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The title of a form sheet, above its scrolling fields. Its Save is a [SaveBar] pinned under the fields (issue #146,
 * which moved it down from the old `SaveSheetHeader`); such sheets also save when closed while valid (#64).
 */
@Composable
fun SheetTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        modifier.fillMaxWidth().padding(bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleLarge,
    )
}
