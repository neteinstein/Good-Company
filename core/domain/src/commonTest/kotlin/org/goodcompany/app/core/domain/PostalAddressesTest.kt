package org.goodcompany.app.core.domain

import kotlinx.coroutines.test.runTest
import org.goodcompany.app.core.domain.model.AddressKind.HOME
import org.goodcompany.app.core.domain.model.AddressKind.WORK
import org.goodcompany.app.core.domain.model.ContactCard
import org.goodcompany.app.core.domain.model.ContactRecord
import org.goodcompany.app.core.domain.model.Friend
import org.goodcompany.app.core.domain.model.PostalAddress
import org.goodcompany.app.core.domain.usecase.BackupJson
import org.goodcompany.app.core.domain.usecase.ContactMerger
import org.goodcompany.app.core.domain.usecase.ContactMerger.hasAppData
import org.goodcompany.app.core.domain.usecase.IdeaMapper
import org.goodcompany.app.core.domain.usecase.PostalAddresses
import org.goodcompany.app.core.domain.usecase.VCardParser
import org.goodcompany.app.core.testing.Fixtures.friend
import org.goodcompany.app.core.testing.TestGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Issue #169: a person's home and work postal addresses. */
class PostalAddressesTest {
    private val contactHome = PostalAddress(HOME, "Rua das Flores 1\n1000-001 Lisboa", fromContact = true)

    private fun record(vararg addresses: PostalAddress) = ContactRecord("1", "Ana", emptyList(), null, addresses = addresses.toList())

    // --- From Contacts ---------------------------------------------------------------------------------------------

    @Test
    fun sync_contactAddressesFollowTheAddressBook_typedOnesAreKept() {
        val ana = friend("contact-1", "Ana") {
            copy(contactId = "1", addresses = listOf(contactHome, PostalAddress(WORK, "Typed office")))
        }
        val merged = ContactMerger.merge(
            listOf(ana),
            listOf(record(PostalAddress(HOME, " New home "), PostalAddress(WORK, "Contact office"))),
        ).single()
        assertEquals(listOf(PostalAddress(HOME, "New home", true), PostalAddress(WORK, "Typed office")), merged.addresses)

        // The contact lost its home address: the contact one goes, the typed one stays.
        assertEquals(listOf(PostalAddress(WORK, "Typed office")), ContactMerger.merge(listOf(merged), listOf(record())).single().addresses)
    }

    @Test
    fun sync_aNewContactBringsItsAddresses_oneOfEachKind_blanksSkipped() {
        val merged = ContactMerger.merge(
            emptyList(),
            listOf(record(PostalAddress(WORK, "  "), PostalAddress(WORK, "Office"), PostalAddress(HOME, "Home"), PostalAddress(HOME, "Second"))),
        ).single()
        assertEquals(listOf(PostalAddress(HOME, "Home", true), PostalAddress(WORK, "Office", true)), merged.addresses)
    }

    @Test
    fun repick_onlyFillsAMissingKind() = runTest {
        val g = TestGraph(listOf(friend("contact-1", "Ana") { copy(contactId = "1", addresses = listOf(PostalAddress(HOME, "Typed home"))) }))
        g.importPickedContacts(listOf(record(PostalAddress(HOME, "Contact home"), PostalAddress(WORK, "Office"))))
        assertEquals(
            listOf(PostalAddress(HOME, "Typed home"), PostalAddress(WORK, "Office", true)),
            g.friendRepo.friends.value.single().addresses,
        )
        // A picker that couldn't read addresses leaves them as they are.
        g.importPickedContacts(listOf(record()))
        assertEquals(2, g.friendRepo.friends.value.single().addresses.size)
        g.importPickedContacts(listOf(ContactRecord("2", "Rui", emptyList(), null, addresses = listOf(PostalAddress(HOME, "Rui's")))))
        assertEquals(listOf(PostalAddress(HOME, "Rui's", true)), g.friendRepo.friends.value.first { it.name == "Rui" }.addresses)
    }

    @Test
    fun typedAddressesAreAppData_contactOnesAreNot() {
        assertTrue(friend("a") { copy(addresses = listOf(PostalAddress(HOME, "Typed"))) }.hasAppData())
        assertFalse(friend("a") { copy(addresses = listOf(contactHome)) }.hasAppData())
    }

    // --- .vcf ------------------------------------------------------------------------------------------------------

    @Test
    fun vcf_readsHomeAndWork_skipsOtherAddresses_andKeepsLines() {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ana Silva
            item1.ADR;type=HOME;type=pref:;Apt 2;Rua das Flores\, 1;Lisboa;;1000-001;Portugal
            ADR;TYPE=OTHER:;;Somewhere;;;;
            ADR;TYPE=work,voice:;;Av. da Liberdade 10\nPiso 3;Lisboa;Lisboa;1250-001;
            ADR;TYPE=HOME:;;Second home;;;;
            END:VCARD
            BEGIN:VCARD
            VERSION:2.1
            FN:Rui
            ADR;HOME;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:;;Rua S=C3=A3o Bento 5;Porto;;4000;
            ADR;WORK:;;;;;;
            END:VCARD
            BEGIN:VCARD
            VERSION:4.0
            FN:Eva
            ADR;TYPE=home;LABEL="10 Downing St\nLondon SW1A 2AA":;;10 Downing St;London;;SW1A 2AA;UK
            END:VCARD
        """.trimIndent()
        val cards = VCardParser.parse(vcf)
        assertEquals(
            listOf(
                PostalAddress(HOME, "Rua das Flores, 1\nApt 2\n1000-001 Lisboa\nPortugal"),
                PostalAddress(WORK, "Av. da Liberdade 10\nPiso 3\n1250-001 Lisboa\nLisboa"),
            ),
            cards[0].addresses,
        )
        assertEquals(listOf(PostalAddress(HOME, "Rua São Bento 5\n4000 Porto")), cards[1].addresses)
        assertEquals(listOf(PostalAddress(HOME, "10 Downing St\nLondon SW1A 2AA")), cards[2].addresses)
    }

    @Test
    fun vcf_importsAddressesAsTypedOnes_andOnlyFillsMissingKinds() = runTest {
        val vcf = """
            BEGIN:VCARD
            VERSION:3.0
            FN:Ana Silva
            ADR;TYPE=HOME:;;Rua A 1;Lisboa;;;
            END:VCARD
        """.trimIndent()
        val g = TestGraph(emptyList())
        g.importContactsFile(vcf)
        assertEquals(listOf(PostalAddress(HOME, "Rua A 1\nLisboa")), g.friendRepo.friends.value.single().addresses)
        g.importContactsFile(vcf.replace("ADR;TYPE=HOME:;;Rua A 1", "ADR;TYPE=WORK:;;Office 2\nADR;TYPE=HOME:;;Rua B 2"))
        assertEquals(
            listOf(PostalAddress(HOME, "Rua A 1\nLisboa"), PostalAddress(WORK, "Office 2")),
            g.friendRepo.friends.value.single().addresses,
        )
    }

    // --- Editing ---------------------------------------------------------------------------------------------------

    @Test
    fun edit_unchangedKeepsTheContactOne_changedBecomesTyped_blankRemoves_missingKindsKept() {
        val work = PostalAddress(WORK, "Office", fromContact = true)
        val current = listOf(contactHome, work)
        assertEquals(current, PostalAddresses.edit(current, mapOf(HOME to " ${contactHome.text} ", WORK to "Office")))
        assertEquals(
            listOf(PostalAddress(HOME, "My own\nhome"), work),
            PostalAddresses.edit(current, mapOf(HOME to "  My own\nhome\n", WORK to "Office")),
        )
        assertEquals(listOf(work), PostalAddresses.edit(current, mapOf(HOME to " ")))
        assertEquals(listOf(PostalAddress(HOME, "Home")), PostalAddresses.edit(emptyList(), mapOf(HOME to "Home", WORK to "")))
    }

    // --- Never written back, never sent to the AI, carried in backups ------------------------------------------------

    @Test
    fun addressesAreNeverWrittenToTheContact() {
        val plain = friend("ana") { copy(likes = listOf("jazz")) }
        assertEquals(ContactCard.from(plain), ContactCard.from(plain.copy(addresses = listOf(PostalAddress(HOME, "Typed")))))
    }

    @Test
    fun addressesAreNeverSentToTheAi() {
        val ana = friend("ana") { copy(addresses = listOf(PostalAddress(HOME, "Rua Secreta 7"), PostalAddress(WORK, "Torre 9"))) }
        val profile = IdeaMapper.profile(ana, emptyList()).toString()
        assertFalse("Rua Secreta" in profile)
        assertFalse("Torre 9" in profile)
    }

    @Test
    fun addressesRoundTripInBackups_andOlderFriendsHaveNone() {
        val ana = friend("ana") { copy(addresses = listOf(contactHome, PostalAddress(WORK, "Office"))) }
        val json = BackupJson.encodeToString(Friend.serializer(), ana)
        assertEquals(ana, BackupJson.decodeFromString(Friend.serializer(), json))
        val older = BackupJson.decodeFromString(Friend.serializer(), """{"id":"x","name":"X"}""")
        assertTrue(older.addresses.isEmpty())
        assertNull(older.address(HOME))
    }
}
