package com.neteinstein.goodcompany.core.ui.l10n

import androidx.compose.runtime.Composable
import com.neteinstein.goodcompany.core.domain.model.Friend
import com.neteinstein.goodcompany.core.domain.model.LinkCategory
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.model.Pronouns
import com.neteinstein.goodcompany.core.domain.repository.BuiltInCircleNames
import com.neteinstein.goodcompany.core.ui.resources.Res
import com.neteinstein.goodcompany.core.ui.resources.anniversary_body
import com.neteinstein.goodcompany.core.ui.resources.anniversary_title
import com.neteinstein.goodcompany.core.ui.resources.anniversary_years
import com.neteinstein.goodcompany.core.ui.resources.circle_family
import com.neteinstein.goodcompany.core.ui.resources.kid_of
import com.neteinstein.goodcompany.core.ui.resources.link_aunt_uncle_he
import com.neteinstein.goodcompany.core.ui.resources.link_aunt_uncle_she
import com.neteinstein.goodcompany.core.ui.resources.link_aunt_uncle_they
import com.neteinstein.goodcompany.core.ui.resources.link_category_family
import com.neteinstein.goodcompany.core.ui.resources.link_category_other
import com.neteinstein.goodcompany.core.ui.resources.link_category_romantic
import com.neteinstein.goodcompany.core.ui.resources.link_child_he
import com.neteinstein.goodcompany.core.ui.resources.link_child_she
import com.neteinstein.goodcompany.core.ui.resources.link_child_they
import com.neteinstein.goodcompany.core.ui.resources.link_colleague_he
import com.neteinstein.goodcompany.core.ui.resources.link_colleague_she
import com.neteinstein.goodcompany.core.ui.resources.link_colleague_they
import com.neteinstein.goodcompany.core.ui.resources.link_cousin_he
import com.neteinstein.goodcompany.core.ui.resources.link_cousin_she
import com.neteinstein.goodcompany.core.ui.resources.link_cousin_they
import com.neteinstein.goodcompany.core.ui.resources.link_dating_he
import com.neteinstein.goodcompany.core.ui.resources.link_dating_she
import com.neteinstein.goodcompany.core.ui.resources.link_dating_they
import com.neteinstein.goodcompany.core.ui.resources.link_ex_he
import com.neteinstein.goodcompany.core.ui.resources.link_ex_she
import com.neteinstein.goodcompany.core.ui.resources.link_ex_they
import com.neteinstein.goodcompany.core.ui.resources.link_friend_he
import com.neteinstein.goodcompany.core.ui.resources.link_friend_she
import com.neteinstein.goodcompany.core.ui.resources.link_friend_they
import com.neteinstein.goodcompany.core.ui.resources.link_grandchild_he
import com.neteinstein.goodcompany.core.ui.resources.link_grandchild_she
import com.neteinstein.goodcompany.core.ui.resources.link_grandchild_they
import com.neteinstein.goodcompany.core.ui.resources.link_grandparent_he
import com.neteinstein.goodcompany.core.ui.resources.link_grandparent_she
import com.neteinstein.goodcompany.core.ui.resources.link_grandparent_they
import com.neteinstein.goodcompany.core.ui.resources.link_in_law_he
import com.neteinstein.goodcompany.core.ui.resources.link_in_law_she
import com.neteinstein.goodcompany.core.ui.resources.link_in_law_they
import com.neteinstein.goodcompany.core.ui.resources.link_niece_nephew_he
import com.neteinstein.goodcompany.core.ui.resources.link_niece_nephew_she
import com.neteinstein.goodcompany.core.ui.resources.link_niece_nephew_they
import com.neteinstein.goodcompany.core.ui.resources.link_parent_he
import com.neteinstein.goodcompany.core.ui.resources.link_parent_she
import com.neteinstein.goodcompany.core.ui.resources.link_parent_they
import com.neteinstein.goodcompany.core.ui.resources.link_partner_he
import com.neteinstein.goodcompany.core.ui.resources.link_partner_she
import com.neteinstein.goodcompany.core.ui.resources.link_partner_they
import com.neteinstein.goodcompany.core.ui.resources.link_sibling_he
import com.neteinstein.goodcompany.core.ui.resources.link_sibling_she
import com.neteinstein.goodcompany.core.ui.resources.link_sibling_they
import com.neteinstein.goodcompany.core.ui.resources.link_spouse_he
import com.neteinstein.goodcompany.core.ui.resources.link_spouse_she
import com.neteinstein.goodcompany.core.ui.resources.link_spouse_they
import com.neteinstein.goodcompany.core.ui.resources.pronouns_he
import com.neteinstein.goodcompany.core.ui.resources.pronouns_she
import com.neteinstein.goodcompany.core.ui.resources.pronouns_they
import com.neteinstein.goodcompany.core.ui.resources.relation_to_me_he
import com.neteinstein.goodcompany.core.ui.resources.relation_to_me_she
import com.neteinstein.goodcompany.core.ui.resources.relation_to_me_they
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

// Words for links between people and "relationship to me" (issue #39). Every LinkType has a she / he / neutral word;
// the neutral one is used when someone has no pronouns (decision F18).

/** The word for [type] when it describes someone with [pronouns] ("Sister", "Brother", "Sibling"). */
fun linkWordRes(type: LinkType, pronouns: Pronouns?): StringResource {
    val (she, he, neutral) = when (type) {
        LinkType.PARENT -> Triple(Res.string.link_parent_she, Res.string.link_parent_he, Res.string.link_parent_they)
        LinkType.CHILD -> Triple(Res.string.link_child_she, Res.string.link_child_he, Res.string.link_child_they)
        LinkType.SIBLING -> Triple(Res.string.link_sibling_she, Res.string.link_sibling_he, Res.string.link_sibling_they)
        LinkType.GRANDPARENT -> Triple(Res.string.link_grandparent_she, Res.string.link_grandparent_he, Res.string.link_grandparent_they)
        LinkType.GRANDCHILD -> Triple(Res.string.link_grandchild_she, Res.string.link_grandchild_he, Res.string.link_grandchild_they)
        LinkType.AUNT_UNCLE -> Triple(Res.string.link_aunt_uncle_she, Res.string.link_aunt_uncle_he, Res.string.link_aunt_uncle_they)
        LinkType.NIECE_NEPHEW -> Triple(Res.string.link_niece_nephew_she, Res.string.link_niece_nephew_he, Res.string.link_niece_nephew_they)
        LinkType.COUSIN -> Triple(Res.string.link_cousin_she, Res.string.link_cousin_he, Res.string.link_cousin_they)
        LinkType.IN_LAW -> Triple(Res.string.link_in_law_she, Res.string.link_in_law_he, Res.string.link_in_law_they)
        LinkType.DATING -> Triple(Res.string.link_dating_she, Res.string.link_dating_he, Res.string.link_dating_they)
        LinkType.SPOUSE -> Triple(Res.string.link_spouse_she, Res.string.link_spouse_he, Res.string.link_spouse_they)
        LinkType.PARTNER -> Triple(Res.string.link_partner_she, Res.string.link_partner_he, Res.string.link_partner_they)
        LinkType.EX -> Triple(Res.string.link_ex_she, Res.string.link_ex_he, Res.string.link_ex_they)
        LinkType.FRIEND -> Triple(Res.string.link_friend_she, Res.string.link_friend_he, Res.string.link_friend_they)
        LinkType.COLLEAGUE -> Triple(Res.string.link_colleague_she, Res.string.link_colleague_he, Res.string.link_colleague_they)
    }
    return when (pronouns) {
        Pronouns.SHE -> she
        Pronouns.HE -> he
        Pronouns.THEY, null -> neutral
    }
}

@Composable
fun linkWord(type: LinkType, pronouns: Pronouns?): String = stringResource(linkWordRes(type, pronouns))

suspend fun linkWordText(type: LinkType, pronouns: Pronouns?): String = getString(linkWordRes(type, pronouns))

/** "Your brother": what [friend] is to you, or null when that isn't set. */
@Composable
fun relationToMeLabel(friend: Friend): String? {
    val type = friend.relationToMe ?: return null
    val format = when (friend.pronouns) {
        Pronouns.SHE -> Res.string.relation_to_me_she
        Pronouns.HE -> Res.string.relation_to_me_he
        Pronouns.THEY, null -> Res.string.relation_to_me_they
    }
    return stringResource(format, linkWord(type, friend.pronouns).lowercase()).replaceFirstChar { it.uppercase() }
}

@Composable
fun pronounsLabel(pronouns: Pronouns): String = stringResource(
    when (pronouns) {
        Pronouns.SHE -> Res.string.pronouns_she
        Pronouns.HE -> Res.string.pronouns_he
        Pronouns.THEY -> Res.string.pronouns_they
    },
)

@Composable
fun linkCategoryLabel(category: LinkCategory): String = stringResource(
    when (category) {
        LinkCategory.FAMILY -> Res.string.link_category_family
        LinkCategory.ROMANTIC -> Res.string.link_category_romantic
        LinkCategory.OTHER -> Res.string.link_category_other
    },
)

/** "Ana’s daughter" (F9): the line under a kid's name, from their parent's first name and their own pronouns. */
@Composable
fun kidOfLabel(kid: Friend, parent: Friend): String =
    stringResource(Res.string.kid_of, parent.displayName.substringBefore(' '), linkWord(LinkType.CHILD, kid.pronouns).lowercase())
        .replaceFirstChar { it.uppercase() }

/** "💞 Ana & Rui’s anniversary". */
@Composable
fun anniversaryTitle(first: String, second: String): String = stringResource(Res.string.anniversary_title, first, second)

/** The built-in circles' names from string resources. */
object CircleNamesFromResources : BuiltInCircleNames {
    override suspend fun family(): String = getString(Res.string.circle_family)
}
