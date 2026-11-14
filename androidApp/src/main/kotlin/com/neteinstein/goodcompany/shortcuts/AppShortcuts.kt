package com.neteinstein.goodcompany.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.Typeface
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.neteinstein.goodcompany.MainActivity
import com.neteinstein.goodcompany.PhotoBitmaps
import com.neteinstein.goodcompany.R
import com.neteinstein.goodcompany.core.domain.usecase.AppLink
import com.neteinstein.goodcompany.core.domain.usecase.ObserveShortcutsUseCase
import com.neteinstein.goodcompany.core.domain.usecase.ShortcutPerson
import com.neteinstein.goodcompany.core.domain.usecase.ShortcutSnapshot
import com.neteinstein.goodcompany.core.ui.components.avatarColorsArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

/**
 * App-icon long-press menu: "Log a meetup" plus the three people you were in touch with most recently
 * ([ObserveShortcutsUseCase]); each opens the Log sheet through `goodcompany://log[/<id>]` (see [MainActivity]).
 * With the app lock or private notifications on, only "Log a meetup" is offered. Kept in step for as long as the
 * process lives, like the widget ([com.neteinstein.goodcompany.widget.NextUpWidget.keepUpdated]).
 */
object AppShortcuts {
    private const val LOG_ID = "log"
    private const val PERSON_PREFIX = "person:"
    private const val DEBOUNCE_MS = 1_000L

    /** Adaptive-icon bitmaps are 108 dp with the visible part in the middle 72 dp; 216 px is xxhdpi-ish. */
    private const val ICON_PX = 216
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    fun keepUpdated(context: Context, observe: ObserveShortcutsUseCase) {
        val app = context.applicationContext
        scope.launch {
            observe().debounce(DEBOUNCE_MS).collect { snapshot -> runCatching { publish(app, snapshot) } }
        }
    }

    /** The shortcut id an opened link came from, for [ShortcutManagerCompat.reportShortcutUsed]. */
    fun idFor(link: AppLink.Log): String = link.friendId?.let { PERSON_PREFIX + it.value } ?: LOG_ID

    fun reportUsed(context: Context, link: AppLink.Log) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, idFor(link)) }
    }

    private fun publish(context: Context, snapshot: ShortcutSnapshot) {
        val logMeetup = ShortcutInfoCompat.Builder(context, LOG_ID)
            .setShortLabel(context.getString(R.string.shortcut_log_meetup_short))
            .setLongLabel(context.getString(R.string.shortcut_log_meetup))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_log))
            .setIntent(intent(context, AppLink.log()))
            .setRank(0)
            .build()
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(1)
        val people = snapshot.people.take(max - 1).mapIndexed { index, person ->
            ShortcutInfoCompat.Builder(context, PERSON_PREFIX + person.id.value)
                .setShortLabel(person.firstName)
                .setLongLabel(person.name)
                .setIcon(IconCompat.createWithAdaptiveBitmap(icon(person)))
                .setIntent(intent(context, AppLink.log(person.id)))
                .setRank(index + 1)
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(context, listOf(logMeetup) + people)
    }

    private fun intent(context: Context, url: String) =
        Intent(Intent.ACTION_VIEW, url.toUri(), context, MainActivity::class.java)

    /** The contact photo full-bleed (the launcher masks it), or initials on the friend's avatar gradient. */
    private fun icon(person: ShortcutPerson): Bitmap {
        person.photo?.let { PhotoBitmaps.square(it, ICON_PX) }?.let { photo ->
            if (photo.width == ICON_PX) return photo
            return photo.scale(ICON_PX, ICON_PX)
        }
        val out = createBitmap(ICON_PX, ICON_PX)
        val canvas = Canvas(out)
        val (top, bottom) = avatarColorsArgb(person.name)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(0f, 0f, ICON_PX.toFloat(), ICON_PX.toFloat(), top, bottom, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, ICON_PX.toFloat(), ICON_PX.toFloat(), fill)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = ICON_PX * 0.26f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        text.getTextBounds(person.initials, 0, person.initials.length, bounds)
        canvas.drawText(person.initials, ICON_PX / 2f, ICON_PX / 2f + bounds.height() / 2f, text)
        return out
    }
}
