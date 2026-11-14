package com.neteinstein.goodcompany.core.ui

import androidx.compose.ui.geometry.Offset
import com.neteinstein.goodcompany.core.domain.model.Birthday
import com.neteinstein.goodcompany.core.ui.components.BirthdayDraft
import com.neteinstein.goodcompany.core.ui.components.CropGeometry
import com.neteinstein.goodcompany.core.ui.platform.PhotoCrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoAndBirthdayEditorsTest {
    // --- Birthday fields (F4) ---

    @Test
    fun dayAndMonth_makeABirthday_andTheYearIsOptional() {
        assertEquals(Birthday(3, 14), BirthdayDraft("14", 3, "").toBirthday(2026))
        assertEquals(Birthday(3, 14, 1990), BirthdayDraft("14", 3, "1990").toBirthday(2026))
        assertEquals(Birthday(2, 29), BirthdayDraft("29", 2).toBirthday(2026), "Feb 29 without a year")
    }

    @Test
    fun anImpossibleDate_isNotABirthday_butAnEmptyOneCanBeSaved() {
        assertNull(BirthdayDraft("31", 4).toBirthday(2026))
        assertNull(BirthdayDraft("29", 2, "2023").toBirthday(2026))
        assertNull(BirthdayDraft("14", null).toBirthday(2026))
        assertNull(BirthdayDraft("14", 3, "2030").toBirthday(2026), "not born yet")
        assertNull(BirthdayDraft("14", 3, "1800").toBirthday(2026))
        assertFalse(BirthdayDraft("31", 4).isValid(2026))
        assertTrue(BirthdayDraft().isEmpty)
        assertTrue(BirthdayDraft().isValid(2026))
        assertNull(BirthdayDraft().toBirthday(2026))
    }

    @Test
    fun aSavedBirthday_isEditedAsItWas() {
        assertEquals(BirthdayDraft("14", 3, ""), BirthdayDraft.of(Birthday(3, 14)))
        assertEquals(BirthdayDraft("14", 3, "1990"), BirthdayDraft.of(Birthday(3, 14, 1990)))
        assertEquals(BirthdayDraft(), BirthdayDraft.of(null))
    }

    // --- Crop editor (F14) ---

    @Test
    fun atZoomOne_theCentredSquareIsCropped() {
        val g = CropGeometry(width = 400, height = 200, frame = 100f)
        assertEquals(PhotoCrop(left = 100, top = 0, side = 200), g.crop(Offset.Zero, 1f))
    }

    @Test
    fun movingAndZooming_pickThePartUnderTheFrame_neverOutsideThePicture() {
        val g = CropGeometry(width = 400, height = 200, frame = 100f)
        // Base scale 0.5: the picture is shown 200 × 100. Dragging right by 50 shows its left edge.
        assertEquals(Offset(50f, 0f), g.clampOffset(Offset(80f, 30f), 1f))
        assertEquals(PhotoCrop(left = 0, top = 0, side = 200), g.crop(Offset(80f, 0f), 1f))
        // Zoom 2: a 100 px square, moved up-left by 25 screen px = 25 source px.
        assertEquals(PhotoCrop(left = 175, top = 75, side = 100), g.crop(Offset(-25f, -25f), 2f))
        assertEquals(1f, g.clampZoom(0.2f))
        assertEquals(CropGeometry.MAX_ZOOM, g.clampZoom(50f))
    }
}
