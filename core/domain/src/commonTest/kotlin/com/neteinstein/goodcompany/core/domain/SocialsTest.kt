package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.SocialLink
import com.neteinstein.goodcompany.core.domain.model.SocialNetwork
import com.neteinstein.goodcompany.core.domain.model.Socials
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Issue #118: profile links per network, from what's typed or pasted. */
class SocialsTest {
    private fun url(network: SocialNetwork, input: String) = Socials.parse(network, input)?.url

    @Test
    fun aHandleBuildsEachNetworksProfileAddress() {
        assertEquals("https://www.instagram.com/ana", url(SocialNetwork.INSTAGRAM, "@ana"))
        assertEquals("https://www.facebook.com/ana.silva", url(SocialNetwork.FACEBOOK, "ana.silva"))
        assertEquals("https://www.tiktok.com/@ana", url(SocialNetwork.TIKTOK, "@ana"))
        assertEquals("https://x.com/ana", url(SocialNetwork.X, "ana"))
        assertEquals("https://www.linkedin.com/in/ana-silva", url(SocialNetwork.LINKEDIN, "ana-silva"))
        assertEquals("https://www.youtube.com/@ana", url(SocialNetwork.YOUTUBE, "@ana"))
        assertEquals("https://www.threads.net/@ana", url(SocialNetwork.THREADS, "ana"))
        assertEquals("https://bsky.app/profile/ana.bsky.social", url(SocialNetwork.BLUESKY, "@ana.bsky.social"))
        assertEquals("https://mastodon.social/@ana", url(SocialNetwork.MASTODON, "@ana@mastodon.social"))
        assertEquals("https://mastodon.social/@ana", url(SocialNetwork.MASTODON, "ana"))
    }

    @Test
    fun aPastedProfileAddressBecomesItsHandle() {
        assertEquals(SocialLink(SocialNetwork.INSTAGRAM, "ana"), Socials.parse(SocialNetwork.INSTAGRAM, "https://www.instagram.com/ana/?hl=pt"))
        assertEquals(SocialLink(SocialNetwork.X, "ana"), Socials.parse(SocialNetwork.X, "twitter.com/ana?s=20"))
        assertEquals(SocialLink(SocialNetwork.LINKEDIN, "ana-silva"), Socials.parse(SocialNetwork.LINKEDIN, "https://linkedin.com/in/ana-silva/"))
        assertEquals(SocialLink(SocialNetwork.TIKTOK, "ana"), Socials.parse(SocialNetwork.TIKTOK, "https://www.tiktok.com/@ana"))
        assertEquals(
            SocialLink(SocialNetwork.BLUESKY, "ana.bsky.social"),
            Socials.parse(SocialNetwork.BLUESKY, "https://bsky.app/profile/ana.bsky.social"),
        )
        assertEquals(SocialLink(SocialNetwork.MASTODON, "@ana@fosstodon.org"), Socials.parse(SocialNetwork.MASTODON, "https://fosstodon.org/@ana"))
    }

    @Test
    fun anyOtherAddressIsKeptWhole_andBlankIsNothing() {
        val odd = "https://www.facebook.com/profile.php?id=123"
        assertEquals(odd, url(SocialNetwork.FACEBOOK, odd))
        assertEquals("https://linktr.ee/ana", url(SocialNetwork.INSTAGRAM, "linktr.ee/ana"))
        assertNull(Socials.parse(SocialNetwork.X, "  "))
    }

    @Test
    fun websitesGetTheirScheme() {
        assertEquals("https://ana.pt", Socials.website(" ana.pt "))
        assertEquals("http://old.ana.pt", Socials.website("http://old.ana.pt"))
        assertNull(Socials.website(""))
    }
}

class SocialsKeepAPersonTest {
    @Test
    fun aContactThatGoesAway_isKeptWhenItHasSocialsOrAWebsite() {
        with(com.neteinstein.goodcompany.core.domain.usecase.ContactMerger) {
            val bare = com.neteinstein.goodcompany.core.testing.Fixtures.friend("ana")
            kotlin.test.assertFalse(bare.hasAppData())
            kotlin.test.assertTrue(bare.copy(socials = listOf(SocialLink(SocialNetwork.X, "ana"))).hasAppData())
            kotlin.test.assertTrue(bare.copy(websites = listOf("https://ana.pt")).hasAppData())
        }
    }
}
