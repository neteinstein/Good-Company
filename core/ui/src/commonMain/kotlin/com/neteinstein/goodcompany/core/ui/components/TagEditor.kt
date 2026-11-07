package com.neteinstein.goodcompany.core.ui.components

import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.core.ui.resources.tags_add
import org.jetbrains.compose.resources.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.ui.format.parseTags

/**
 * Chips for a list of short strings (likes, dislikes, kid's favourite things) with an inline add field.
 * Stateless apart from the text being typed; changes go out through [onAdd] / [onRemove].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    tags: List<String>,
    placeholder: String,
    onAdd: (List<String>) -> Unit,
    onRemove: (String) -> Unit,
    modifier: Modifier = Modifier,
    chipColor: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    fun submit() {
        val parsed = parseTags(draft)
        if (parsed.isNotEmpty()) onAdd(parsed)
        draft = ""
    }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                AnimatedVisibility(visible = true, enter = fadeIn() + scaleIn()) {
                    InputChip(
                        selected = false,
                        onClick = { onRemove(tag) },
                        label = { Text(tag) },
                        trailingIcon = { Icon(Icons.Rounded.Close, stringResource(Res.string.remove_item, tag), Modifier.size(InputChipDefaults.IconSize)) },
                        colors = InputChipDefaults.inputChipColors(containerColor = chipColor),
                        border = null,
                    )
                }
            }
        }
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = { Text(placeholder) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = ::submit, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, stringResource(Res.string.tags_add)) }
            },
        )
    }
}
