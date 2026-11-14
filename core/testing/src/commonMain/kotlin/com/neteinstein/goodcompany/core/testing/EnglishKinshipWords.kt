package com.neteinstein.goodcompany.core.testing

import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.domain.repository.KinshipWords

/** The English kinship search words (as in `:core:ui` `values/strings.xml`), for ViewModel tests. */
val EnglishKinshipWords = KinshipWords(
    possessives = listOf("my"),
    connectors = listOf("and", "&"),
    words = mapOf(
        LinkType.NIECE_NEPHEW to listOf("nieces", "nephews", "niece", "nephew"),
        LinkType.COUSIN to listOf("cousins", "cousin"),
        LinkType.SIBLING to listOf("siblings", "sisters", "brothers", "sister", "brother"),
        LinkType.PARENT to listOf("parents", "parent", "mother", "father", "mum", "mom", "dad"),
        LinkType.CHILD to listOf("children", "kids", "child", "daughters", "sons", "daughter", "son"),
        LinkType.GRANDPARENT to listOf("grandparents", "grandmother", "grandfather", "grandma", "grandpa"),
        LinkType.GRANDCHILD to listOf("grandchildren", "grandkids", "granddaughter", "grandson"),
        LinkType.AUNT_UNCLE to listOf("aunts", "uncles", "aunt", "uncle"),
        LinkType.IN_LAW to listOf("in-laws", "in-law"),
    ),
)
