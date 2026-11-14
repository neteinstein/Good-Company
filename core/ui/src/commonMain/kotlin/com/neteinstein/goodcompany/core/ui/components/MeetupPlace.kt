package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.GeoPoint
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.domain.usecase.NamedPlaces
import com.neteinstein.goodcompany.core.domain.usecase.PlaceBook
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.log_clear_location
import com.neteinstein.goodcompany.core.ui.resources.log_locating
import com.neteinstein.goodcompany.core.ui.resources.log_use_location
import com.neteinstein.goodcompany.core.ui.resources.place_add_person
import com.neteinstein.goodcompany.core.ui.resources.place_change
import com.neteinstein.goodcompany.core.ui.resources.place_first_time
import com.neteinstein.goodcompany.core.ui.resources.place_name_hint
import com.neteinstein.goodcompany.core.ui.resources.place_named_auto
import com.neteinstein.goodcompany.core.ui.resources.place_next_time
import com.neteinstein.goodcompany.core.ui.resources.place_suggest_hint
import com.neteinstein.goodcompany.core.ui.resources.place_usually_with
import com.neteinstein.goodcompany.core.ui.resources.place_what_is_this
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/** Where a meetup was (F11), as both check-in sheets show it. Built by their ViewModels. */
data class MeetupPlaceState(
    /** Typed by hand: free text without a GPS fix, the new place's name with one. */
    val name: String = "",
    val location: GeoPoint? = null,
    val isLocating: Boolean = false,
    /** The named place the fix is in (null after "Change"): the meetup takes its name by itself. */
    val match: NamedPlaces.Match? = null,
    /** No named place covers the fix: "What place is this?" is asked for the first time. */
    val firstTimeHere: Boolean = false,
    /** "Usually here with": people often met at [match], not yet picked. Never ticked by themselves. */
    val suggested: List<Friend> = emptyList(),
    /** How many of the last meetups there [suggested] comes from. */
    val suggestedFrom: Int = 0,
)

/**
 * The place part of a check-in. Without a fix: a free-text field and "Use location". At a named place: its name,
 * filled in by itself, with "Change". At a new spot: "What place is this?" (optional; leaving it empty keeps just the
 * coordinates). No place-search or map service is ever called.
 */
@Composable
fun MeetupPlace(
    state: MeetupPlaceState,
    freeTextLabel: String,
    onName: (String) -> Unit,
    onUseLocation: () -> Unit,
    onClearLocation: () -> Unit,
    onChange: () -> Unit,
    onAddPerson: (FriendId) -> Unit,
) {
    val location = state.location
    val match = state.match
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when {
            location != null && match != null -> KnownPlace(match, onChange, onClearLocation)
            location != null -> NewPlace(state, location, onName, onClearLocation)
            else -> {
                OutlinedTextField(
                    state.name,
                    onName,
                    Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Place, null) },
                    label = { Text(freeTextLabel) },
                    singleLine = true,
                )
                if (state.isLocating) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.log_locating))
                    }
                } else {
                    AssistChip(
                        onClick = onUseLocation,
                        label = { Text(stringResource(Res.string.log_use_location)) },
                        leadingIcon = { Icon(Icons.Rounded.MyLocation, null, Modifier.size(18.dp)) },
                    )
                }
            }
        }
        if (state.suggested.isNotEmpty()) UsualPeople(state.suggested, state.suggestedFrom, onAddPerson)
    }
}

@Composable
private fun KnownPlace(match: NamedPlaces.Match, onChange: () -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.large) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(match.place.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.place_named_auto, match.distanceM.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onChange) { Text(stringResource(Res.string.place_change)) }
            IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, stringResource(Res.string.log_clear_location), Modifier.size(18.dp)) }
        }
    }
}

@Composable
private fun NewPlace(state: MeetupPlaceState, location: GeoPoint, onName: (String) -> Unit, onClear: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.large) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Place, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(Res.string.place_what_is_this),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, stringResource(Res.string.log_clear_location), Modifier.size(18.dp)) }
            }
            OutlinedTextField(
                state.name,
                onName,
                Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(Res.string.place_name_hint)) },
                singleLine = true,
            )
            val coordinates = Place(null, location.latitude, location.longitude).label.orEmpty()
            Text(
                if (state.firstTimeHere) stringResource(Res.string.place_first_time, coordinates) else coordinates,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                stringResource(Res.string.place_next_time, NamedPlace.DEFAULT_RADIUS_M),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

/** "Usually here with": tap to add. A suggestion only; nobody is ticked by themselves. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsualPeople(people: List<Friend>, from: Int, onAdd: (FriendId) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(Res.string.place_usually_with), style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            people.forEach { f ->
                val label = stringResource(Res.string.place_add_person, f.name)
                InputChip(
                    selected = false,
                    onClick = { onAdd(f.id) },
                    label = { Text(f.name.substringBefore(' ')) },
                    leadingIcon = { Icon(Icons.Rounded.Add, label, Modifier.size(18.dp)) },
                    avatar = { FriendAvatar(f, size = 20.dp) },
                )
            }
        }
        Text(
            pluralStringResource(Res.plurals.place_suggest_hint, from, from),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Builds the sheet's [MeetupPlaceState] from what's typed and the named places: at a named place (unless [changed])
 * it names itself and suggests the people usually there, leaving out [picked].
 */
fun meetupPlaceState(
    name: String,
    location: GeoPoint?,
    isLocating: Boolean,
    changed: Boolean,
    book: PlaceBook,
    picked: Collection<FriendId>,
    everyone: List<Friend>,
): MeetupPlaceState {
    val found = book.match(location)
    val match = found.takeUnless { changed }
    val byId = everyone.associateBy { it.id }
    return MeetupPlaceState(
        name = name,
        location = location,
        isLocating = isLocating,
        match = match,
        firstTimeHere = location != null && found == null,
        suggested = match?.let { m -> book.usualPeople(m.place.id, picked).mapNotNull(byId::get) }.orEmpty(),
        suggestedFrom = match?.let { book.recentMeetups(it.place.id) } ?: 0,
    )
}
