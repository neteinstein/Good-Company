package com.neteinstein.goodcompany.core.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.neteinstein.goodcompany.core.domain.usecase.DRIVE_FILE_SCOPE
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.AuthenticationServices.ASPresentationAnchor
import platform.AuthenticationServices.ASWebAuthenticationPresentationContextProvidingProtocol
import platform.AuthenticationServices.ASWebAuthenticationSession
import platform.AuthenticationServices.ASWebAuthenticationSessionErrorCodeCanceledLogin
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSBundle
import platform.Foundation.NSCharacterSet
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSJSONSerialization
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLComponents
import platform.Foundation.NSURLQueryItem
import platform.Foundation.NSURLSession
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.create
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.dataUsingEncoding
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
import platform.Foundation.stringByAddingPercentEncodingWithAllowedCharacters
import platform.Security.SecRandomCopyBytes
import platform.Security.kSecRandomDefault
import platform.UIKit.UIWindow
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * The iOS OAuth client ID, from Info.plist `GoogleDriveClientId` (set via `GOOGLE_DRIVE_IOS_CLIENT_ID` in
 * Config.xcconfig). Null = Drive backup isn't set up for this build and the Settings row is hidden.
 */
internal val googleDriveClientId: String? =
    (NSBundle.mainBundle.objectForInfoDictionaryKey("GoogleDriveClientId") as? String)
        ?.trim()
        ?.takeIf { it.endsWith(".apps.googleusercontent.com") }

/**
 * Google OAuth for installed apps: authorization code + PKCE in an ASWebAuthenticationSession, then the code is
 * exchanged for an access token. iOS clients have no secret, so nothing sensitive ships in the app.
 */
@Composable
actual fun rememberGoogleDriveAuthorizer(onResult: (DriveAuthResult) -> Unit): () -> Unit {
    val callback = rememberUpdatedState(onResult)
    val flow = remember { DriveAuthFlow() }
    return { flow.start { result -> callback.value(result) } }
}

private class DriveAuthFlow {
    private var session: ASWebAuthenticationSession? = null
    private val anchor = WindowAnchor()

    fun start(done: (DriveAuthResult) -> Unit) {
        val clientId = googleDriveClientId ?: return done(DriveAuthResult.NotConfigured)
        // Google's iOS redirect: the client ID reversed, as a custom scheme.
        val scheme = "com.googleusercontent.apps." + clientId.removeSuffix(".apps.googleusercontent.com")
        val redirect = "$scheme:/oauth2redirect"
        val verifier = randomUrlSafe(48)
        val url = NSURL(
            string = "https://accounts.google.com/o/oauth2/v2/auth?" + form(
                "client_id" to clientId,
                "redirect_uri" to redirect,
                "response_type" to "code",
                "scope" to DRIVE_FILE_SCOPE,
                "code_challenge" to challenge(verifier),
                "code_challenge_method" to "S256",
            ),
        )
        val auth = ASWebAuthenticationSession(uRL = url, callbackURLScheme = scheme) { callbackUrl: NSURL?, error: NSError? ->
            session = null
            when {
                error != null && error.code == ASWebAuthenticationSessionErrorCodeCanceledLogin -> done(DriveAuthResult.Cancelled)
                error != null -> done(DriveAuthResult.Failed(error.localizedDescription))
                else -> {
                    val code = callbackUrl?.queryValue("code")
                    if (code == null) {
                        done(DriveAuthResult.Failed(callbackUrl?.queryValue("error") ?: "No authorization code"))
                    } else {
                        exchange(clientId, redirect, code, verifier, done)
                    }
                }
            }
        }
        auth.presentationContextProvider = anchor
        session = auth
        if (!auth.start()) {
            session = null
            done(DriveAuthResult.Failed("Couldn't open Google sign-in"))
        }
    }

    @OptIn(BetaInteropApi::class)
    private fun exchange(clientId: String, redirect: String, code: String, verifier: String, done: (DriveAuthResult) -> Unit) {
        val request = NSMutableURLRequest(uRL = NSURL(string = "https://oauth2.googleapis.com/token"))
        request.setHTTPMethod("POST")
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField = "Content-Type")
        val body = form(
            "client_id" to clientId,
            "code" to code,
            "code_verifier" to verifier,
            "grant_type" to "authorization_code",
            "redirect_uri" to redirect,
        )
        request.setHTTPBody(NSString.create(string = body).dataUsingEncoding(NSUTF8StringEncoding))
        NSURLSession.sharedSession.dataTaskWithRequest(request) { data: NSData?, _, error: NSError? ->
            val token = data?.let { tokenFrom(it) }
            val result = when {
                token != null -> DriveAuthResult.Token(token)
                error != null -> DriveAuthResult.Failed(error.localizedDescription)
                else -> DriveAuthResult.Failed("Token exchange failed")
            }
            dispatch_async(dispatch_get_main_queue()) { done(result) }
        }.resume()
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun tokenFrom(data: NSData): String? =
        (NSJSONSerialization.JSONObjectWithData(data, 0u, null) as? Map<*, *>)?.get("access_token") as? String
}

/** Presents the Google sheet over the app's key window. A class (Kotlin/Native can't make Obj-C singletons). */
private class WindowAnchor : NSObject(), ASWebAuthenticationPresentationContextProvidingProtocol {
    override fun presentationAnchorForWebAuthenticationSession(session: ASWebAuthenticationSession): ASPresentationAnchor =
        keyWindow() ?: UIWindow()
}

private fun NSURL.queryValue(name: String): String? =
    NSURLComponents(uRL = this, resolvingAgainstBaseURL = false).queryItems
        ?.filterIsInstance<NSURLQueryItem>()
        ?.firstOrNull { it.name == name }
        ?.value

/** RFC 3986 unreserved characters only, so `:`, `/`, `&` and `=` in values are always escaped. */
private val unreserved = NSCharacterSet.characterSetWithCharactersInString(
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~",
)

@OptIn(BetaInteropApi::class)
private fun form(vararg pairs: Pair<String, String>): String = pairs.joinToString("&") { (k, v) ->
    k + "=" + (NSString.create(string = v).stringByAddingPercentEncodingWithAllowedCharacters(unreserved) ?: v)
}

@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private fun randomUrlSafe(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    bytes.usePinned { SecRandomCopyBytes(kSecRandomDefault, byteCount.convert(), it.addressOf(0)) }
    return Base64.UrlSafe.encode(bytes).trimEnd('=')
}

/** PKCE S256: base64url(SHA-256(verifier)). */
@OptIn(ExperimentalForeignApi::class, ExperimentalEncodingApi::class)
private fun challenge(verifier: String): String {
    val input = verifier.encodeToByteArray()
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { i -> digest.usePinned { o -> CC_SHA256(i.addressOf(0), input.size.convert(), o.addressOf(0)) } }
    return Base64.UrlSafe.encode(digest.toByteArray()).trimEnd('=')
}
