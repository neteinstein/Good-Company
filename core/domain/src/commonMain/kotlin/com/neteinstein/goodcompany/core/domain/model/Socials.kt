package com.neteinstein.goodcompany.core.domain.model

import kotlinx.serialization.Serializable

/** The networks a person's profile can be on (issue #118, a fixed list with brand icons). */
enum class SocialNetwork(val displayName: String) {
    INSTAGRAM("Instagram"),
    FACEBOOK("Facebook"),
    TIKTOK("TikTok"),
    X("X"),
    LINKEDIN("LinkedIn"),
    YOUTUBE("YouTube"),
    THREADS("Threads"),
    BLUESKY("Bluesky"),
    MASTODON("Mastodon"),
}

/**
 * One profile (issue #118): the [network] and the [handle] as [Socials.parse] cleaned it ("ana", "ana.bsky.social",
 * "@ana@mastodon.social"), or a full URL that didn't look like a profile on that network. [url] opens it.
 */
@Serializable
data class SocialLink(val network: SocialNetwork, val handle: String) {
    val url: String get() = Socials.url(network, handle)
}

/** Builds and reads profile links (issue #118). Pure: the app never fetches them. */
object Socials {
    private val hosts = mapOf(
        SocialNetwork.INSTAGRAM to listOf("instagram.com"),
        SocialNetwork.FACEBOOK to listOf("facebook.com", "fb.com", "m.facebook.com"),
        SocialNetwork.TIKTOK to listOf("tiktok.com"),
        SocialNetwork.X to listOf("x.com", "twitter.com"),
        SocialNetwork.LINKEDIN to listOf("linkedin.com"),
        SocialNetwork.YOUTUBE to listOf("youtube.com", "m.youtube.com"),
        SocialNetwork.THREADS to listOf("threads.net", "threads.com"),
        SocialNetwork.BLUESKY to listOf("bsky.app"),
    )

    /** The profile's web address; a handle that is already a URL opens as it is. */
    fun url(network: SocialNetwork, handle: String): String {
        if (handle.isUrl()) return handle
        return when (network) {
            SocialNetwork.INSTAGRAM -> "https://www.instagram.com/$handle"
            SocialNetwork.FACEBOOK -> "https://www.facebook.com/$handle"
            SocialNetwork.TIKTOK -> "https://www.tiktok.com/@$handle"
            SocialNetwork.X -> "https://x.com/$handle"
            SocialNetwork.LINKEDIN -> "https://www.linkedin.com/in/$handle"
            SocialNetwork.YOUTUBE -> "https://www.youtube.com/@$handle"
            SocialNetwork.THREADS -> "https://www.threads.net/@$handle"
            SocialNetwork.BLUESKY -> "https://bsky.app/profile/$handle"
            SocialNetwork.MASTODON -> {
                val (user, server) = handle.removePrefix("@").split('@', limit = 2).let { it[0] to it.getOrNull(1) }
                if (server == null) "https://mastodon.social/@$user" else "https://$server/@$user"
            }
        }
    }

    /**
     * What was typed or pasted ("@ana", "instagram.com/ana/", "https://x.com/ana?s=20") as a link, or null when blank.
     * A profile address on the network becomes its handle; any other URL is kept whole.
     */
    fun parse(network: SocialNetwork, input: String): SocialLink? {
        val text = input.trim()
        if (text.isEmpty()) return null
        if (network == SocialNetwork.MASTODON) return SocialLink(network, mastodon(text))
        val bare = text.removePrefix("https://").removePrefix("http://").removePrefix("www.")
        val host = bare.substringBefore('/').lowercase()
        val known = hosts[network].orEmpty()
        if (host in known) {
            val path = bare.substringAfter('/', "").substringBefore('?').substringBefore('#').trim('/')
            val handle = when (network) {
                SocialNetwork.LINKEDIN -> path.removePrefix("in/")
                SocialNetwork.BLUESKY -> path.removePrefix("profile/")
                SocialNetwork.FACEBOOK -> path.takeUnless { it.startsWith("profile.php") }
                else -> path
            }?.removePrefix("@")?.substringBefore('/')
            return SocialLink(network, handle?.takeIf { it.isNotEmpty() } ?: text.withScheme())
        }
        if (text.isUrl() || '/' in text) return SocialLink(network, text.withScheme())
        return SocialLink(network, text.removePrefix("@"))
    }

    /** "@ana@mastodon.social", "ana@mastodon.social" or "https://mastodon.social/@ana" → "@ana@mastodon.social". */
    private fun mastodon(text: String): String {
        val bare = text.removePrefix("https://").removePrefix("http://")
        if ('/' in bare) {
            val server = bare.substringBefore('/')
            val user = bare.substringAfter('/').removePrefix("@").substringBefore('/').substringBefore('?')
            return if (user.isNotEmpty()) "@$user@$server" else text.withScheme()
        }
        return "@" + text.removePrefix("@")
    }

    /** A website as typed ("ana.pt") with its scheme ("https://ana.pt"), or null when blank. */
    fun website(input: String): String? = input.trim().takeIf { it.isNotEmpty() }?.withScheme()

    private fun String.isUrl() = startsWith("https://") || startsWith("http://")

    private fun String.withScheme() = if (isUrl()) this else "https://$this"
}
