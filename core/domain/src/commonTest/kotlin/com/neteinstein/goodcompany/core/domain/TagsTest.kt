package com.neteinstein.goodcompany.core.domain

import com.neteinstein.goodcompany.core.domain.model.AppSettings
import com.neteinstein.goodcompany.core.domain.model.Kid
import com.neteinstein.goodcompany.core.domain.model.Relation
import com.neteinstein.goodcompany.core.domain.model.Relative
import com.neteinstein.goodcompany.core.domain.usecase.SampleData
import com.neteinstein.goodcompany.core.domain.usecase.Tag
import com.neteinstein.goodcompany.core.domain.usecase.Tags
import com.neteinstein.goodcompany.core.testing.FixedClock
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TagsTest {
    private val ana = friend("ana", "Ana") {
        copy(
            likes = listOf("Sushi", "jazz"),
            food = listOf("Pastéis de nata"),
            interests = listOf("jazz"),
            likesAddedAt = mapOf("Sushi" to at(2026, 1, 2)),
            kids = listOf(Kid("leo", "Leo", likes = listOf("sushi", "LEGO"))),
        )
    }
    private val pedro = friend("pedro", "Pedro") { copy(dislikes = listOf("sushi"), drinks = listOf("Café")) }
    private val rui = friend("rui", "Rui") {
        copy(
            likes = listOf("sumo"),
            food = listOf("sushi"),
            foodAddedAt = mapOf("sushi" to at(2026, 3, 1)),
            relatives = listOf(Relative("tiago", Relation.PARTNER, "Tiago", likes = listOf("cafe"))),
        )
    }
    private val everyone = listOf(ana, pedro, rui)

    @Test
    fun normalize_ignoresCaseAccentsAndSpaces() {
        assertEquals("pasteis de nata", Tags.normalize("  Pastéis   de NATA "))
        assertEquals("cafe", Tags.normalize("Café"))
        assertEquals(Tags.normalize("Ștefan Țară"), Tags.normalize("stefan tara"))
        assertEquals("creme brulee", Tags.normalize("Crème Brûlée"))
        assertEquals("oeuvre", Tags.normalize("Œuvre"))
    }

    @Test
    fun catalog_countsEachPersonOnce_andALikeCanBeSomeoneElsesDislike() {
        val catalog = Tags.catalog(everyone).associateBy { it.name }
        // Ana has jazz twice (likes and interests): one person.
        assertEquals(listOf("Ana"), catalog.getValue("jazz").likedBy)
        // "sushi": Leo (Ana's kid) and Rui like it, Pedro doesn't.
        assertEquals(listOf("Leo", "Rui"), catalog.getValue("sushi").likedBy)
        assertEquals(listOf("Pedro"), catalog.getValue("sushi").dislikedBy)
        assertEquals(3, catalog.getValue("sushi").uses)
        assertEquals(listOf("Tiago"), catalog.getValue("cafe").likedBy)
        // Spellings stay apart in the list (that's what merging is for); most used first.
        assertEquals("sushi", Tags.catalog(everyone).first().name)
    }

    @Test
    fun suggest_mostUsedWhenBlank_thenMatchesStartingWithTheQueryFirst() {
        val catalog = Tags.catalog(everyone)
        assertEquals("sushi", Tags.suggest(catalog, "").first().name)
        // "su": prefix matches, most used first ("sushi", 3 uses); ties ("sumo", "Sushi": 1 each) A–Z.
        assertEquals(listOf("sushi", "sumo", "Sushi"), Tags.suggest(catalog, "su").map { it.name })
        // Accents and case don't matter.
        assertEquals(setOf("Café", "cafe"), Tags.suggest(catalog, "CAFÉ").map { it.name }.toSet())
        // A word inside the tag ranks after prefixes: "nata" finds "Pastéis de nata".
        assertEquals(listOf("Pastéis de nata"), Tags.suggest(catalog, "nata").map { it.name })
        // What the list already has (any spelling) isn't offered again.
        assertEquals(listOf("sumo"), Tags.suggest(catalog, "su", exclude = listOf("SUSHI")).map { it.name })
        assertEquals(2, Tags.suggest(catalog, "", limit = 2).size)
    }

    @Test
    fun suggest_prefersWordStartsOverInnerMatches() {
        val catalog = listOf(Tag("bossa nova", listOf("a", "b", "c")), Tag("nova vaga", listOf("a")), Tag("casanova", listOf("a", "b")))
        assertEquals(listOf("nova vaga", "bossa nova", "casanova"), Tags.suggest(catalog, "nova").map { it.name })
    }

    @Test
    fun canonical_reusesAnExistingSpelling() {
        val catalog = Tags.catalog(everyone)
        assertEquals("sushi", Tags.canonical(catalog, " SUSHI "))
        assertEquals("ramen", Tags.canonical(catalog, " ramen "))
    }

    @Test
    fun duplicates_groupSpellingsOfOneTag_mostUsedFirst() {
        val groups = Tags.duplicates(Tags.catalog(everyone))
        assertEquals(listOf(listOf("sushi", "Sushi"), listOf("Café", "cafe")), groups.map { g -> g.map { it.name } })
        assertEquals(Tags.signature(listOf("sushi", "Sushi")), Tags.signature(listOf("Sushi", "sushi")))
    }

    @Test
    fun rename_reachesEveryField_kidsAndRelatives_andMergesWithoutDuplicates() {
        val renamed = everyone.map { Tags.rename(it, setOf("Sushi", "sushi"), "Sushi") }.associateBy { it.id.value }
        val a = renamed.getValue("ana")
        assertEquals(listOf("Sushi", "jazz"), a.likes)
        assertEquals(listOf("Sushi", "LEGO"), a.kids.single().likes)
        assertEquals(listOf("Sushi"), renamed.getValue("pedro").dislikes)
        val r = renamed.getValue("rui")
        assertEquals(listOf("Sushi"), r.food)
        // When-added dates follow the new name.
        assertEquals(mapOf("Sushi" to at(2026, 3, 1)), r.foodAddedAt)
        assertEquals(mapOf("Sushi" to at(2026, 1, 2)), a.likesAddedAt)

        val both = friend("x") {
            copy(
                likes = listOf("sushi", "Sushi", "ramen"),
                likesAddedAt = mapOf(
                    "sushi" to at(2026, 5, 1),
                    "Sushi" to at(2026, 4, 1),
                ),
            )
        }
        val merged = Tags.rename(both, setOf("sushi"), "Sushi")
        assertEquals(listOf("Sushi", "ramen"), merged.likes)
        assertEquals(mapOf("Sushi" to at(2026, 4, 1)), merged.likesAddedAt)
        assertEquals(both, Tags.rename(both, setOf("pizza"), "Pizza"))

        val relative = Tags.rename(rui, setOf("cafe"), "Café").relatives.single()
        assertEquals(listOf("Café"), relative.likes)
    }

    @Test
    fun remove_andRestore() {
        val removed = Tags.remove(rui, "sushi")
        assertEquals(emptyList(), removed.food)
        assertEquals(emptyMap(), removed.foodAddedAt)
        val edited = removed.copy(job = "Chef")
        val restored = Tags.restore(edited, rui)
        assertEquals(rui.food, restored.food)
        assertEquals(rui.foodAddedAt, restored.foodAddedAt)
        assertEquals("Chef", restored.job)
    }

    @Test
    fun useCases_renameMergeAndDeleteEveryoneInOneGo_withUndo() = runTest {
        val g = TestGraph(friends = everyone)
        assertEquals(3, g.renameTag(setOf("sushi"), "Sushi"))
        val catalog = g.observeTags().first()
        assertTrue(catalog.none { it.name == "sushi" })
        assertEquals(4, catalog.first { it.name == "Sushi" }.uses)
        assertEquals(0, g.renameTag(setOf("Sushi"), "  "))

        val removed = g.deleteTag("Sushi")
        // Leo, Ana's kid, is a person of his own in the graph (F9), so his likes change too.
        assertEquals(setOf("ana", "pedro", "rui", "leo"), removed.before.map { it.id.value }.toSet())
        assertTrue(g.observeTags().first().none { it.name == "Sushi" })
        g.restoreTag(removed)
        assertEquals(4, g.observeTags().first().first { it.name == "Sushi" }.uses)
    }

    @Test
    fun keepBoth_hidesThatGroupUntilANewSpellingShowsUp() = runTest {
        val g = TestGraph(friends = everyone, settings = AppSettings())
        assertEquals(2, g.observeTagDuplicates().first().size)
        g.keepTagSpellings(listOf("Café", "cafe"))
        assertEquals(listOf("sushi"), g.observeTagDuplicates().first().map { it.first().name })
        g.friendRepo.upsert(friend("new") { copy(likes = listOf("CAFÉ")) })
        assertEquals(2, g.observeTagDuplicates().first().size)
    }

    @Test
    fun sampleData_sharesTags_includingALikeThatIsSomeoneElsesDislike() {
        val catalog = Tags.catalog(SampleData.build(FixedClock()).friends)
        assertTrue(catalog.count { it.uses > 1 } >= 3)
        assertTrue(catalog.any { it.likedBy.isNotEmpty() && it.dislikedBy.isNotEmpty() })
    }
}
