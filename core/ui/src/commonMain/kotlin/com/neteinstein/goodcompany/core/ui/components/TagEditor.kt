package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.Label
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.Tags
import com.neteinstein.goodcompany.core.ui.format.parseTags
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.remove_item
import com.neteinstein.goodcompany.core.ui.resources.tags_add
import com.neteinstein.goodcompany.core.ui.resources.tags_add_new
import com.neteinstein.goodcompany.core.ui.resources.tags_chip_count
import com.neteinstein.goodcompany.core.ui.resources.tags_disliked_by_one
import com.neteinstein.goodcompany.core.ui.resources.tags_disliked_by_others
import com.neteinstein.goodcompany.core.ui.resources.tags_liked_by_one
import com.neteinstein.goodcompany.core.ui.resources.tags_liked_by_others
import com.neteinstein.goodcompany.core.ui.resources.tags_matches
import com.neteinstein.goodcompany.core.ui.resources.tags_more
import com.neteinstein.goodcompany.core.ui.resources.tags_most_used
import com.neteinstein.goodcompany.core.ui.resources.tags_people_join
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/**
 * Chips for a list of short strings (likes, dislikes, kid's favourite things) with an inline add field.
 * Stateless apart from the text being typed; changes go out through [onAdd] / [onRemove].
 *
 * [catalog] is the shared tag list (F16). While the field has focus it offers the most used tags as chips (nothing
 * typed) or the matches for what's typed, ignoring case and accents, with who else has each tag. A typed tag that
 * already exists in another spelling is saved with that spelling.
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
    catalog: List<Tag> = emptyList(),
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var showAll by rememberSaveable { mutableStateOf(false) }
    fun submit() {
        val parsed = parseTags(draft).map { Tags.canonical(catalog, it) }.distinct()
        if (parsed.isNotEmpty()) onAdd(parsed)
        draft = ""
    }
    fun pick(tag: Tag) {
        onAdd(listOf(tag.name))
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
                        trailingIcon = {
                            Icon(Icons.Rounded.Close, stringResource(Res.string.remove_item, tag), Modifier.size(InputChipDefaults.IconSize))
                        },
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
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            trailingIcon = {
                IconButton(onClick = ::submit, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, stringResource(Res.string.tags_add)) }
            },
        )
        AnimatedVisibility(focused && catalog.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            if (draft.isBlank()) {
                val top = Tags.suggest(catalog, "", exclude = tags, limit = if (showAll) MORE_LIMIT else MOST_USED_LIMIT)
                if (top.isNotEmpty()) {
                    MostUsed(top, canShowMore = !showAll && top.size == MOST_USED_LIMIT, onPick = ::pick, onMore = {
                        showAll = true
                    })
                }
            } else {
                val matches = Tags.suggest(catalog, draft, exclude = tags, limit = MATCH_LIMIT)
                val exact = matches.any { it.key == Tags.normalize(draft) } || tags.any { Tags.normalize(it) == Tags.normalize(draft) }
                Matches(matches, newTag = draft.trim().takeUnless { exact || it.isEmpty() }, onPick = ::pick, onAddNew = ::submit)
            }
        }
    }
}

private const val MOST_USED_LIMIT = 8
private const val MORE_LIMIT = 40
private const val MATCH_LIMIT = 5

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MostUsed(tags: List<Tag>, canShowMore: Boolean, onPick: (Tag) -> Unit, onMore: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        SuggestionsLabel(stringResource(Res.string.tags_most_used))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tags.forEach { tag ->
                SuggestionChip(onClick = { onPick(tag) }, label = { Text(stringResource(Res.string.tags_chip_count, tag.name, tag.uses)) })
            }
            if (canShowMore) SuggestionChip(onClick = onMore, label = { Text(stringResource(Res.string.tags_more)) })
        }
    }
}

@Composable
private fun Matches(matches: List<Tag>, newTag: String?, onPick: (Tag) -> Unit, onAddNew: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        if (matches.isNotEmpty()) SuggestionsLabel(stringResource(Res.string.tags_matches))
        matches.forEach { tag ->
            ListItem(
                leadingContent = { Icon(Icons.AutoMirrored.Outlined.Label, null) },
                headlineContent = { Text(tag.name) },
                supportingContent = tagPeople(tag)?.let { { Text(it) } },
                trailingContent = { Icon(Icons.Rounded.Add, stringResource(Res.string.tags_add), tint = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable { onPick(tag) },
            )
        }
        if (newTag != null) {
            ListItem(
                leadingContent = { Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(stringResource(Res.string.tags_add_new, newTag), color = MaterialTheme.colorScheme.primary) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                modifier = Modifier.clickable(onClick = onAddNew),
            )
        }
    }
}

@Composable
private fun SuggestionsLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/** "Ana and 3 others like it · Pedro doesn't" (null when nobody has it yet). */
@Composable
private fun tagPeople(tag: Tag): String? {
    val liked = when (tag.likedBy.size) {
        0 -> null
        1 -> stringResource(Res.string.tags_liked_by_one, tag.likedBy.first())
        else -> pluralStringResource(Res.plurals.tags_liked_by_others, tag.likedBy.size - 1, tag.likedBy.first(), tag.likedBy.size - 1)
    }
    val disliked = when (tag.dislikedBy.size) {
        0 -> null
        1 -> stringResource(Res.string.tags_disliked_by_one, tag.dislikedBy.first())
        else -> pluralStringResource(Res.plurals.tags_disliked_by_others, tag.dislikedBy.size - 1, tag.dislikedBy.first(), tag.dislikedBy.size - 1)
    }
    return when {
        liked != null && disliked != null -> stringResource(Res.string.tags_people_join, liked, disliked)
        else -> liked ?: disliked
    }
}
