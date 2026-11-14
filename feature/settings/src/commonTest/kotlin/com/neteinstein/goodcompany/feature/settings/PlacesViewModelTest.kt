package com.neteinstein.goodcompany.feature.settings

import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.Hangout
import com.neteinstein.goodcompany.core.domain.model.NamedPlace
import com.neteinstein.goodcompany.core.domain.model.Place
import com.neteinstein.goodcompany.core.testing.Fixtures.at
import com.neteinstein.goodcompany.core.testing.Fixtures.friend
import com.neteinstein.goodcompany.core.testing.TestGraph
import com.neteinstein.goodcompany.core.testing.collecting
import com.neteinstein.goodcompany.core.testing.runViewModelTest
import com.neteinstein.goodcompany.feature.settings.ui.screens.places.PlacesMessage
import com.neteinstein.goodcompany.feature.settings.ui.screens.places.PlacesViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlacesViewModelTest {
    private val g = TestGraph(friends = listOf(friend("marta", "Marta Lopes"), friend("rui")))

    private fun vm() = PlacesViewModel(g.observePlaces, g.renamePlace, g.deletePlace, g.restorePlace, g.mergePlaces)

    private suspend fun seed() {
        g.hangoutRepo.upsertPlace(NamedPlace("marta", "Marta’s house", 38.7139, -9.1394))
        g.hangoutRepo.upsertPlace(NamedPlace("graca", "Graça’s place", 38.7143, -9.1394))
        g.hangoutRepo.add(Hangout("h1", listOf(FriendId("marta"), FriendId("rui")), at(2026, 3, 1), Place("Marta’s house", placeId = "marta")))
        g.hangoutRepo.add(Hangout("h2", listOf(FriendId("marta")), at(2026, 3, 2), Place("Marta’s house", placeId = "marta")))
        g.hangoutRepo.add(Hangout("h3", listOf(FriendId("rui")), at(2026, 3, 3), Place("Graça’s place", placeId = "graca")))
    }

    @Test
    fun listsPlaces_mostVisitedFirst_withWhoIsUsuallyThereAndANearbyDuplicate() = runViewModelTest {
        seed()
        val state = collecting(vm().state)
        val rows = state.value.places
        assertEquals(listOf("Marta’s house", "Graça’s place"), rows.map { it.place.name })
        assertEquals(2, rows.first().meetups)
        assertEquals(listOf("Marta Lopes", "Rui"), rows.first().usually.map { it.name })
        assertEquals("graca", rows.first().nearby?.place?.id)
    }

    @Test
    fun renameMergeAndDeleteWithUndo() = runViewModelTest {
        seed()
        val vm = vm()
        val state = collecting(vm.state)
        vm.onRename("marta", "Marta & Rui’s")
        assertEquals(listOf("Marta & Rui’s", "Marta & Rui’s", "Graça’s place"), g.hangoutRepo.hangouts.value.map { it.place?.name })

        vm.onMerge("graca", "marta")
        assertEquals(PlacesMessage.Merged("Marta & Rui’s"), state.value.message)
        assertEquals(listOf("marta"), state.value.places.map { it.place.id })
        assertEquals(3, state.value.places.single().meetups)
        vm.onMessageShown()
        assertNull(state.value.message)

        vm.onDelete("marta")
        assertEquals(PlacesMessage.Deleted("Marta & Rui’s"), state.value.message)
        assertTrue(state.value.places.isEmpty())
        vm.onUndoDelete()
        assertEquals(3, state.value.places.single().meetups)
        assertNull(state.value.recentlyDeleted)
    }
}
