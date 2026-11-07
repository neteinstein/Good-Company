package com.neteinstein.goodcompany.feature.onboarding.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChildCare
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.components.BlobBackground
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.platformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.onboarding.ui.components.OrbitIllustration
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel

private data class Page(val title: String, val body: String, val main: ImageVector, val satellites: List<ImageVector>)

private val PAGES = listOf(
    Page(
        "Your people,\nremembered",
        "A little CRM for friendship. Keep what your friends love, their kids' names and the dates that matter — all on your device.",
        Icons.Rounded.Favorite,
        listOf(Icons.Rounded.MusicNote, Icons.Rounded.LocalCafe, Icons.Rounded.ChildCare, Icons.Rounded.Cake),
    ),
    Page(
        "One tap:\n“I'm with…”",
        "Log who you're with and where. See the last time you were with any group — and who you haven't seen in a while.",
        Icons.Rounded.Diversity3,
        listOf(Icons.Rounded.Place, Icons.Rounded.Schedule, Icons.Rounded.Favorite),
    ),
    Page(
        "Never miss\nwhat matters",
        "Birthdays, a friend's surgery, “book tickets with Rui” — gentle reminders at the right time, plus AI ideas for things to do together.",
        Icons.Rounded.Notifications,
        listOf(Icons.Rounded.Cake, Icons.Rounded.Event, Icons.Rounded.AutoAwesome, Icons.Rounded.ChildCare),
    ),
)

@Composable
fun OnboardingScreen(onDone: () -> Unit, viewModel: OnboardingViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestContacts = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactsPermissionResult)
    val requestNotifications = rememberPermissionRequester(AppPermission.NOTIFICATIONS, viewModel::onNotificationsPermissionResult)
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    OnboardingContent(
        state = state,
        capabilities = platformCapabilities,
        onImportContacts = requestContacts,
        onAllowNotifications = requestNotifications,
        onExploreSample = viewModel::onExploreWithSample,
        onFinish = viewModel::onFinish,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    capabilities: PlatformCapabilities,
    onImportContacts: () -> Unit,
    onAllowNotifications: () -> Unit,
    onExploreSample: () -> Unit,
    onFinish: () -> Unit,
    initialPage: Int = 0,
) {
    val pageCount = PAGES.size + 1
    val pager = rememberPagerState(initialPage = initialPage) { pageCount }
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        BlobBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.End) {
                AnimatedContent(pager.currentPage < PAGES.size) { show ->
                    if (show) {
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES.size) } }) { Text("Skip") }
                    } else {
                        Spacer(Modifier.height(48.dp))
                    }
                }
            }
            HorizontalPager(pager, modifier = Modifier.weight(1f).widthIn(max = 560.dp)) { page ->
                if (page <
                    PAGES.size
                ) {
                    IntroPage(PAGES[page])
                } else {
                    SetupPage(state, capabilities, onImportContacts, onAllowNotifications, onExploreSample)
                }
            }
            Dots(pager.currentPage, pageCount)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(horizontal = 24.dp)) {
                AnimatedContent(pager.currentPage == pageCount - 1) { last ->
                    Button(
                        onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                        enabled = !state.isWorking,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(if (last) startLabel(state) else "Next", style = MaterialTheme.typography.titleMedium) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun startLabel(state: OnboardingUiState) = if ((state.contactsImported ?: 0) > 0) "Let's go" else "Start with an empty list"

@Composable
private fun IntroPage(page: Page) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitIllustration(page.main, page.satellites)
        Spacer(Modifier.height(28.dp))
        Text(page.title, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(page.body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun SetupPage(
    state: OnboardingUiState,
    capabilities: PlatformCapabilities,
    onImportContacts: () -> Unit,
    onAllowNotifications: () -> Unit,
    onExploreSample: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text("Let's set you up", style = MaterialTheme.typography.displaySmall)
        Text(
            "Everything is optional and you can change it later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (capabilities.hasAddressBook) {
            SetupItem(
                icon = Icons.Rounded.Contacts,
                title = "Bring in your contacts",
                body = when {
                    state.contactsImported != null -> "${state.contactsImported} contacts imported. Tap ♥ on the real friends."
                    state.contactsDenied -> "No problem — you can add friends by hand."
                    else -> "We read names, phone numbers and birthdays. Nothing leaves your device."
                },
                done = state.contactsImported != null,
                action = "Import",
                busy = state.isWorking && state.contactsImported == null,
                onClick = onImportContacts,
            )
        }
        if (capabilities.hasBackgroundNotifications) {
            SetupItem(
                icon = Icons.Rounded.Notifications,
                title = "Birthday & check-in reminders",
                body = when (state.notificationsGranted) {
                    true -> "You're all set — we'll nudge you at the right time."
                    false -> "Reminders are off. You can turn them on in Settings."
                    null -> "A nudge on birthdays, important days and when it's been too long."
                },
                done = state.notificationsGranted == true,
                action = "Allow",
                onClick = onAllowNotifications,
            )
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Just looking around?", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Explore with a sample circle of friends — remove it anytime from Settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onExploreSample, enabled = !state.isWorking) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Explore with sample friends")
                }
            }
        }
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary) },
            headlineContent = { Text("Private by design", style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text("No account, no server. SMS are never read.") },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
        )
    }
}

@Composable
private fun SetupItem(icon: ImageVector, title: String, body: String, done: Boolean, action: String, onClick: () -> Unit, busy: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            AnimatedContent(Triple(done, busy, action)) { (isDone, isBusy, label) ->
                when {
                    isDone -> Icon(Icons.Rounded.Check, "Done", tint = MaterialTheme.colorScheme.primary)
                    isBusy -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else -> OutlinedButton(onClick = onClick) { Text(label) }
                }
            }
        }
    }
}

@Composable
private fun Dots(current: Int, count: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 24.dp else 8.dp)
            val color by animateColorAsState(if (i == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            Box(Modifier.height(8.dp).width(width).background(color, CircleShape))
        }
    }
}
