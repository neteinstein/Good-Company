package com.neteinstein.goodcompany.core.ui

import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.repository.KinshipWords
import com.neteinstein.goodcompany.core.domain.usecase.KinshipQuery
import com.neteinstein.goodcompany.core.ui.l10n.KinshipWordsFromResources
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The kinship search (F7, issue #41) against the real words in each language's `strings.xml`, read as
 * [KinshipWordsFromResources] reads them.
 */
class KinshipSearchWordsTest {
    private fun words(lang: String): KinshipWords {
        val dir = if (lang.isEmpty()) "values" else "values-$lang"
        val file = File("src/commonMain/composeResources/$dir/strings.xml")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        val strings = (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent
        }
        fun split(key: String) = strings.getValue(key).split('|').map { it.trim() }.filter { it.isNotEmpty() }
        val keys = mapOf(
            LinkType.PARENT to "parent", LinkType.CHILD to "child", LinkType.SIBLING to "sibling",
            LinkType.GRANDPARENT to "grandparent", LinkType.GRANDCHILD to "grandchild", LinkType.AUNT_UNCLE to "aunt_uncle",
            LinkType.NIECE_NEPHEW to "niece_nephew", LinkType.COUSIN to "cousin", LinkType.IN_LAW to "in_law",
            LinkType.STEP_PARENT to "step_parent", LinkType.STEP_CHILD to "step_child",
            LinkType.HALF_SIBLING to "half_sibling", LinkType.STEP_SIBLING to "step_sibling",
        )
        return KinshipWords(split("kin_search_my"), split("kin_search_and"), keys.mapValues { (_, k) -> split("kin_search_$k") })
    }

    private val languages = listOf("", "pt", "es", "fr", "ro")

    @Test
    fun everyFamilyRelation_hasSearchWords_inEveryLanguage_andNoWordMeansTwoThings() {
        val searchable = LinkType.entries.filter { KinshipWordsFromResources.kinSearchRes(it) != null }
        assertEquals(LinkType.entries.filter { it.isFamily }, searchable)
        languages.forEach { lang ->
            val w = words(lang)
            assertTrue(w.possessives.isNotEmpty() && w.connectors.isNotEmpty(), lang)
            searchable.forEach { assertTrue(w.words[it].orEmpty().isNotEmpty(), "$lang $it") }
            val owners = w.words.flatMap { (type, list) -> list.map { KinshipQuery.normalize(it) to type } }.groupBy({ it.first }, { it.second })
            val clashes = owners.filterValues { it.toSet().size > 1 }
            assertTrue(clashes.isEmpty(), "$lang: $clashes")
        }
    }

    @Test
    fun theUsualQueries_workInEveryLanguage() {
        val cases = mapOf(
            "" to
                listOf("my nieces and nephews" to LinkType.NIECE_NEPHEW, "my cousins" to LinkType.COUSIN, "My Aunts & Uncles" to LinkType.AUNT_UNCLE),
            "pt" to
                listOf(
                    "os meus sobrinhos" to LinkType.NIECE_NEPHEW,
                    "os meus primos e primas" to LinkType.COUSIN,
                    "as minhas tias" to LinkType.AUNT_UNCLE,
                ),
            "es" to listOf("mis sobrinos y sobrinas" to LinkType.NIECE_NEPHEW, "mis primos" to LinkType.COUSIN, "mis tios" to LinkType.AUNT_UNCLE),
            "fr" to
                listOf("mes nièces et neveux" to LinkType.NIECE_NEPHEW, "mes cousins" to LinkType.COUSIN, "mes demi-freres" to LinkType.HALF_SIBLING),
            "ro" to listOf("nepoții mei" to LinkType.NIECE_NEPHEW, "verii mei" to LinkType.COUSIN, "unchii și mătușile" to LinkType.AUNT_UNCLE),
        )
        cases.forEach { (lang, queries) ->
            val w = words(lang)
            queries.forEach { (query, type) -> assertEquals(setOf(type), KinshipQuery.parse(query, w), "$lang: $query") }
            assertEquals(null, KinshipQuery.parse("Leonor", w), lang)
        }
    }
}
