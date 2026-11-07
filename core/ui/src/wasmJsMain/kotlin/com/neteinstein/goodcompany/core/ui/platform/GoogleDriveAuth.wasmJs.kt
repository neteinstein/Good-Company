package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_FILE_SCOPE

/**
 * The web OAuth client ID, from `<meta name="google-drive-client-id" content="...">` in index.html. Its
 * "Authorized JavaScript origins" must include the site's origin. Null = Drive backup hidden on the web.
 */
internal fun googleDriveClientId(): String? = metaContent("google-drive-client-id")
    ?.trim()
    ?.takeIf { it.endsWith(".apps.googleusercontent.com") }

/** Google Identity Services token client. The script is loaded ahead of time so the popup opens on the tap itself. */
@Composable
actual fun rememberGoogleDriveAuthorizer(onResult: (DriveAuthResult) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val clientId = googleDriveClientId()
    LaunchedEffect(clientId) { if (clientId != null) loadGoogleIdentity() }
    return {
        if (clientId == null) {
            callback.value(DriveAuthResult.NotConfigured)
        } else {
            requestGoogleToken(
                clientId,
                DRIVE_FILE_SCOPE,
                onToken = { callback.value(DriveAuthResult.Token(it)) },
                onError = { error ->
                    callback.value(
                        when (error) {
                            "popup_closed", "access_denied" -> DriveAuthResult.Cancelled
                            else -> DriveAuthResult.Failed(error)
                        },
                    )
                },
            )
        }
    }
}

private fun metaContent(name: String): String? = js("document.querySelector('meta[name=\"' + name + '\"]')?.getAttribute('content') || null")

private fun loadGoogleIdentity(): Unit = js(
    """{
        if (window.google && google.accounts && google.accounts.oauth2) return;
        if (document.getElementById('gsi-client')) return;
        var s = document.createElement('script');
        s.id = 'gsi-client';
        s.src = 'https://accounts.google.com/gsi/client';
        s.async = true;
        document.head.appendChild(s);
    }""",
)

private fun requestGoogleToken(clientId: String, scope: String, onToken: (String) -> Unit, onError: (String) -> Unit): Unit = js(
    """{
        if (!(window.google && google.accounts && google.accounts.oauth2)) { onError('not_loaded'); return; }
        google.accounts.oauth2.initTokenClient({
            client_id: clientId,
            scope: scope,
            callback: function (r) {
                if (r && r.access_token) onToken(r.access_token); else onError((r && r.error) || 'access_denied');
            },
            error_callback: function (e) { onError((e && e.type) || 'error'); }
        }).requestAccessToken();
    }""",
)
