package com.neteinstein.goodcompany.feature.hangouts.ui.screens.gifts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.GiftOccasion
import com.neteinstein.goodcompany.core.domain.model.GiftStatus
import com.neteinstein.goodcompany.core.ui.components.LocalDatePickerDialog
import com.neteinstein.goodcompany.core.ui.format.shortLabel
import com.neteinstein.goodcompany.core.ui.l10n.occasionLabel
import com.neteinstein.goodcompany.core.ui.resources.cancel
import com.neteinstein.goodcompany.core.ui.resources.save
import com.neteinstein.goodcompany.feature.hangouts.resources.Res
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_create
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_date
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_name
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_name_hint
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_no_date
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_no_occasion
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_list_occasion
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_status_action
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_status_bought
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_status_given
import com.neteinstein.goodcompany.feature.hangouts.resources.gift_status_to_buy
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource
import kotlin.math.abs
import kotlin.math.roundToLong
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

/** "€35", "€12.50", "35 lei", "35" — the currency as people wrote it in their prices. */
internal fun money(amount: Double, currency: String?): String {
    val cents = (abs(amount) * 100).roundToLong()
    val number = if (cents % 100 == 0L) "${cents / 100}" else "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"
    return when {
        currency == null -> number
        currency.length == 1 -> currency + number
        else -> "$number $currency"
    }
}

/** The icon of a list, from what its gifts are recorded as. */
internal fun occasionEmoji(occasion: GiftOccasion?): String = when (occasion) {
    GiftOccasion.CHRISTMAS -> "🎄"
    GiftOccasion.BIRTHDAY -> "🎂"
    GiftOccasion.THANK_YOU -> "💐"
    GiftOccasion.JUST_BECAUSE, null -> "🎁"
}

@Composable
internal fun OccasionBadge(occasion: GiftOccasion?, modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = modifier.size(40.dp)) {
        Box(contentAlignment = Alignment.Center) { Text(occasionEmoji(occasion), style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
internal fun statusLabel(status: GiftStatus): String = stringResource(
    when (status) {
        GiftStatus.TO_BUY -> Res.string.gift_status_to_buy
        GiftStatus.BOUGHT -> Res.string.gift_status_bought
        GiftStatus.GIVEN -> Res.string.gift_status_given
    },
)

/** The to buy / bought / given chip: tapping it moves the gift along. */
@Composable
internal fun StatusChip(title: String, status: GiftStatus, onClick: () -> Unit) {
    val label = statusLabel(status)
    val description = stringResource(Res.string.gift_status_action, title, label)
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (status) {
        GiftStatus.TO_BUY -> colors.surfaceVariant to colors.onSurfaceVariant
        GiftStatus.BOUGHT -> colors.tertiaryContainer to colors.onTertiaryContainer
        GiftStatus.GIVEN -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        colors = AssistChipDefaults.assistChipColors(containerColor = container, labelColor = content),
        border = null,
        modifier = Modifier.semantics { contentDescription = description },
    )
}

/** New list or "Edit list": a name, what its gifts are recorded as, and an optional date. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GiftListEditorDialog(
    initial: NewGiftList,
    today: LocalDate,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, GiftOccasion?, LocalDate?) -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial.title) }
    var occasion by remember { mutableStateOf(initial.occasion) }
    var date by remember { mutableStateOf(initial.date) }
    var picking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(title, occasion, date) }, enabled = title.isNotBlank()) {
                Text(stringResource(if (isNew) Res.string.gift_list_create else CoreRes.string.save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.gift_list_name)) },
                    placeholder = { Text(stringResource(Res.string.gift_list_name_hint, (date ?: today).year)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(stringResource(Res.string.gift_list_occasion), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (GiftOccasion.entries + listOf(null)).forEach { o ->
                        FilterChip(
                            selected = occasion == o,
                            onClick = { occasion = o },
                            label = { Text(if (o == null) stringResource(Res.string.gift_list_no_occasion) else occasionLabel(o)) },
                        )
                    }
                }
                Text(stringResource(Res.string.gift_list_date), style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
                    OutlinedButton(onClick = { picking = true }) {
                        Icon(Icons.Rounded.Event, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(date?.let { "${it.shortLabel()} ${it.year}" } ?: stringResource(Res.string.gift_list_no_date))
                    }
                    if (date != null) TextButton(onClick = { date = null }) { Text(stringResource(Res.string.gift_list_no_date)) }
                }
            }
        },
    )
    if (picking) LocalDatePickerDialog(initial = date ?: today, onDismiss = { picking = false }, onPicked = { date = it })
}

/** A one-field dialog: a budget, or a new gift (title and price). */
@Composable
internal fun TextFieldsDialog(
    title: String,
    fields: List<Pair<String, String>>,
    numeric: Set<Int> = emptySet(),
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    confirm: String = stringResource(CoreRes.string.save),
    requireFirst: Boolean = false,
) {
    var values by remember { mutableStateOf(fields.map { it.second }) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = { onSave(values) }, enabled = !requireFirst || values.first().isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(CoreRes.string.cancel)) } },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                fields.forEachIndexed { i, (label, _) ->
                    OutlinedTextField(
                        value = values[i],
                        onValueChange = { v -> values = values.toMutableList().also { it[i] = v } },
                        label = { Text(label) },
                        singleLine = true,
                        keyboardOptions = if (i in numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}
