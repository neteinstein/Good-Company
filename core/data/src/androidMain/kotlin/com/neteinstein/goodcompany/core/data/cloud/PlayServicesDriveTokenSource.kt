package com.neteinstein.goodcompany.core.data.cloud

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import com.neteinstein.goodcompany.core.domain.repository.DriveTokenSource
import com.neteinstein.goodcompany.core.domain.repository.SilentDriveToken
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_SCOPES
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Drive tokens for the automatic backup (issue #166) from Play services' `AuthorizationClient`, the same client the
 * Settings button uses. Once the user granted the Drive scopes in the app, `authorize` returns a token with no UI;
 * when it would need to show something (`hasResolution`), only the app can ask, so this says [SilentDriveToken.SignInNeeded].
 */
class PlayServicesDriveTokenSource(private val context: Context) : DriveTokenSource {
    override suspend fun tokenWithoutUi(): SilentDriveToken = try {
        val request = AuthorizationRequest.builder().setRequestedScopes(DRIVE_SCOPES.map(::Scope)).build()
        val result = Identity.getAuthorizationClient(context).authorize(request).await()
        val token = result.accessToken
        when {
            result.hasResolution() -> SilentDriveToken.SignInNeeded
            token != null -> SilentDriveToken.Token(token)
            else -> SilentDriveToken.Failed(network = false, detail = "No access token")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        when (e.statusCode) {
            CommonStatusCodes.DEVELOPER_ERROR -> SilentDriveToken.NotConfigured
            CommonStatusCodes.SIGN_IN_REQUIRED, CommonStatusCodes.RESOLUTION_REQUIRED -> SilentDriveToken.SignInNeeded
            CommonStatusCodes.NETWORK_ERROR, CommonStatusCodes.TIMEOUT -> SilentDriveToken.Failed(network = true, detail = e.message)
            else -> SilentDriveToken.Failed(network = false, detail = e.message)
        }
    } catch (e: Exception) {
        SilentDriveToken.Failed(network = false, detail = e.message)
    }

    override suspend fun forget(accessToken: String) {
        runCatching {
            Identity.getAuthorizationClient(context).clearToken(ClearTokenRequest.builder().setToken(accessToken).build()).await()
        }.onFailure { if (it is CancellationException) throw it }
    }
}

/** Waits for a Play services [Task] (no extra coroutines library). */
internal suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}
