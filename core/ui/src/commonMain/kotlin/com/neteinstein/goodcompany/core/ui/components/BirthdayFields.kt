package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.ui.format.monthLong
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.birthday_day
import com.neteinstein.goodcompany.core.ui.resources.birthday_month
import com.neteinstein.goodcompany.core.ui.resources.birthday_year
import kotlinx.datetime.Month
import org.jetbrains.compose.resources.stringResource

/**
 * What is typed into [BirthdayFields]: day and year as text, the month as 1–12 (null = not chosen). The year is
 * optional (F4): day and month alone make a birthday, and no age is shown until a year is added.
 */
data class BirthdayDraft(val day: String = "", val month: Int? = null, val year: String = "") {
    /** True when nothing has been entered: saving it clears the birthday. */
    val isEmpty: Boolean get() = day.isBlank() && month == null && year.isBlank()

    /** The birthday entered, or null when it is empty or not a real date. */
    fun toBirthday(latestYear: Int): Birthday? {
        val d = day.trim().toIntOrNull() ?: return null
        val m = month ?: return null
        val y = year.trim().takeIf { it.isNotEmpty() }?.let { it.toIntOrNull() ?: return null }
        if (y != null && y !in MIN_YEAR..latestYear) return null
        // Without a year, Feb 29 is allowed (it falls on Feb 28 in other years).
        if (d !in 1..Birthday.daysInMonth(y ?: LEAP_YEAR, m)) return null
        return Birthday(m, d, y)
    }

    /** Can be saved: either empty (no birthday) or a real date. */
    fun isValid(latestYear: Int): Boolean = isEmpty || toBirthday(latestYear) != null

    companion object {
        private const val MIN_YEAR = 1900
        private const val LEAP_YEAR = 2000

        fun of(birthday: Birthday?): BirthdayDraft = birthday?.let {
            BirthdayDraft(it.day.toString(), it.month, it.year?.toString().orEmpty())
        } ?: BirthdayDraft()
    }
}

/** Day, month and an optional year, side by side (F4). [latestYear] is the latest year accepted (this year). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayFields(draft: BirthdayDraft, onChange: (BirthdayDraft) -> Unit, latestYear: Int, modifier: Modifier = Modifier) {
    val digits = KeyboardOptions(keyboardType = KeyboardType.Number)
    val dayInvalid = draft.day.isNotBlank() && draft.month != null && !draft.isValid(latestYear) && draft.year.isBlank()
    val yearInvalid = draft.year.isNotBlank() && !draft.isValid(latestYear)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            draft.day,
            { value -> onChange(draft.copy(day = value.filter(Char::isDigit).take(2))) },
            label = { Text(stringResource(Res.string.birthday_day)) },
            singleLine = true,
            isError = dayInvalid,
            keyboardOptions = digits,
            modifier = Modifier.weight(0.8f),
        )
        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.weight(1.4f)) {
            OutlinedTextField(
                draft.month?.let { monthLong(Month(it)) }.orEmpty(),
                {},
                readOnly = true,
                singleLine = true,
                label = { Text(stringResource(Res.string.birthday_month)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Month.entries.forEach { month ->
                    DropdownMenuItem(
                        text = { Text(monthLong(month)) },
                        onClick = {
                            onChange(draft.copy(month = month.ordinal + 1))
                            expanded = false
                        },
                    )
                }
            }
        }
        OutlinedTextField(
            draft.year,
            { value -> onChange(draft.copy(year = value.filter(Char::isDigit).take(4))) },
            label = { Text(stringResource(Res.string.birthday_year)) },
            singleLine = true,
            isError = yearInvalid,
            keyboardOptions = digits,
            modifier = Modifier.weight(1f),
        )
    }
}
