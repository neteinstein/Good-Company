package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Sms
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.neteinstein.goodcompany.core.ui.format.urlEncode
import com.neteinstein.goodcompany.core.ui.platform.ShareOutcome
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberTextSharer
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.copied_to_clipboard
import com.neteinstein.goodcompany.core.ui.resources.send_ai_personalise
import com.neteinstein.goodcompany.core.ui.resources.send_ai_writing
import com.neteinstein.goodcompany.core.ui.resources.send_share
import com.neteinstein.goodcompany.core.ui.resources.send_sms
import com.neteinstein.goodcompany.core.ui.resources.send_title_message
import com.neteinstein.goodcompany.core.ui.resources.send_whatsapp
import org.jetbrains.compose.resources.stringResource

/** WhatsApp deep link, straight into the chat when we know the number (otherwise WhatsApp asks who to send it to). */
fun whatsAppLink(phone: String?, text: String): String {
    val digits = phone?.filter(Char::isDigit).orEmpty()
    return "https://wa.me/$digits?text=${urlEncode(text)}"
}

/** `sms:` link with a prefilled body. */
fun smsLink(phone: String, text: String, separator: String = platformCapabilities.smsBodySeparator): String =
    "sms:${phone.filter { it.isDigit() || it == '+' }}${separator}body=${urlEncode(text)}"

/**
 * Bottom sheet to review a drafted message and send it through WhatsApp, a text message or the share sheet.
 * Nothing is sent by the app itself — the user always confirms in the messaging app.
 *
 * [suggestion] replaces the text whenever it changes (e.g. when the AI finishes a draft).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SendMessageSheet(
    title: String,
    suggestion: String,
    phone: String?,
    onDismiss: () -> Unit,
    onSent: () -> Unit = {},
    isGenerating: Boolean = false,
    onPersonalise: (() -> Unit)? = null,
    header: (@Composable () -> Unit)? = null,
) {
    var text by remember { mutableStateOf(suggestion) }
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(suggestion) { text = suggestion }
    val uri = LocalUriHandler.current
    val sharer = rememberTextSharer()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            header?.invoke()
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(stringResource(Res.string.send_title_message)) },
                enabled = !isGenerating,
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                shape = MaterialTheme.shapes.medium,
            )
            if (onPersonalise != null) {
                TextButton(onClick = onPersonalise, enabled = !isGenerating) {
                    if (isGenerating) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(if (isGenerating) Res.string.send_ai_writing else Res.string.send_ai_personalise))
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = text.isNotBlank() && !isGenerating
                Button(onClick = {
                    uri.openUri(whatsAppLink(phone, text))
                    onSent()
                }, enabled = ready) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.send_whatsapp))
                }
                if (phone != null) {
                    FilledTonalButton(onClick = {
                        uri.openUri(smsLink(phone, text))
                        onSent()
                    }, enabled = ready) {
                        Icon(Icons.Rounded.Sms, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(Res.string.send_sms))
                    }
                }
                OutlinedButton(onClick = {
                    copied = sharer.share(text) == ShareOutcome.COPIED
                    onSent()
                }, enabled = ready) {
                    Icon(Icons.Rounded.IosShare, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.send_share))
                }
            }
            AnimatedVisibility(copied) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(Res.string.copied_to_clipboard),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
