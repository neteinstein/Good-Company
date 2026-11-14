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
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.ui.components.BlobBackground
import com.neteinstein.goodcompany.core.ui.platform.AppPermission
import com.neteinstein.goodcompany.core.ui.platform.PlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.currentPlatformCapabilities
import com.neteinstein.goodcompany.core.ui.platform.rememberContactPicker
import com.neteinstein.goodcompany.core.ui.platform.rememberPermissionRequester
import com.neteinstein.goodcompany.feature.onboarding.resources.Res
import com.neteinstein.goodcompany.feature.onboarding.resources.allow
import com.neteinstein.goodcompany.feature.onboarding.resources.contacts_picked
import com.neteinstein.goodcompany.feature.onboarding.resources.done
import com.neteinstein.goodcompany.feature.onboarding.resources.explore_sample
import com.neteinstein.goodcompany.feature.onboarding.resources.lets_go
import com.neteinstein.goodcompany.feature.onboarding.resources.looking_body
import com.neteinstein.goodcompany.feature.onboarding.resources.looking_title
import com.neteinstein.goodcompany.feature.onboarding.resources.next
import com.neteinstein.goodcompany.feature.onboarding.resources.notifications_body
import com.neteinstein.goodcompany.feature.onboarding.resources.notifications_off
import com.neteinstein.goodcompany.feature.onboarding.resources.notifications_on
import com.neteinstein.goodcompany.feature.onboarding.resources.notifications_title
import com.neteinstein.goodcompany.feature.onboarding.resources.page1_body
import com.neteinstein.goodcompany.feature.onboarding.resources.page1_title
import com.neteinstein.goodcompany.feature.onboarding.resources.page2_body
import com.neteinstein.goodcompany.feature.onboarding.resources.page2_title
import com.neteinstein.goodcompany.feature.onboarding.resources.page3_body
import com.neteinstein.goodcompany.feature.onboarding.resources.page3_title
import com.neteinstein.goodcompany.feature.onboarding.resources.pick
import com.neteinstein.goodcompany.feature.onboarding.resources.pick_body
import com.neteinstein.goodcompany.feature.onboarding.resources.pick_body_write
import com.neteinstein.goodcompany.feature.onboarding.resources.pick_title
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_body
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_denied
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_on_device
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_pick_instead
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_show
import com.neteinstein.goodcompany.feature.onboarding.resources.primer_title
import com.neteinstein.goodcompany.feature.onboarding.resources.private_body
import com.neteinstein.goodcompany.feature.onboarding.resources.private_title
import com.neteinstein.goodcompany.feature.onboarding.resources.setup_body
import com.neteinstein.goodcompany.feature.onboarding.resources.setup_title
import com.neteinstein.goodcompany.feature.onboarding.resources.skip
import com.neteinstein.goodcompany.feature.onboarding.resources.start_empty
import com.neteinstein.goodcompany.feature.onboarding.ui.components.OrbitIllustration
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

private data class Page(val title: StringResource, val body: StringResource, val main: ImageVector, val satellites: List<ImageVector>)

private val PAGES = listOf(
    Page(
        Res.string.page1_title,
        Res.string.page1_body,
        Icons.Rounded.Favorite,
        listOf(Icons.Rounded.MusicNote, Icons.Rounded.LocalCafe, Icons.Rounded.ChildCare, Icons.Rounded.Cake),
    ),
    Page(
        Res.string.page2_title,
        Res.string.page2_body,
        Icons.Rounded.Diversity3,
        listOf(Icons.Rounded.Place, Icons.Rounded.Schedule, Icons.Rounded.Favorite),
    ),
    Page(
        Res.string.page3_title,
        Res.string.page3_body,
        Icons.Rounded.Notifications,
        listOf(Icons.Rounded.Cake, Icons.Rounded.Event, Icons.Rounded.AutoAwesome, Icons.Rounded.ChildCare),
    ),
)

/**
 * First run. [contactList] draws the "Choose friends" list (it lives in another feature module, so the app passes it
 * in); it is shown full screen once the contacts permission is granted and reports how many people it added.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    contactList: @Composable (onClose: (added: Int) -> Unit) -> Unit = { it(0) },
    viewModel: OnboardingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestContactList = rememberPermissionRequester(AppPermission.CONTACTS, viewModel::onContactListPermissionResult)
    val requestNotifications = rememberPermissionRequester(AppPermission.NOTIFICATIONS, viewModel::onNotificationsPermissionResult)
    LaunchedEffect(state.finished) { if (state.finished) onDone() }
    val capabilities = currentPlatformCapabilities()
    // The picker needs no permission: "Pick people one by one instead" never asks for it (decision D2).
    val pickContact = rememberContactPicker(onPicked = viewModel::onContactsPicked)
    // A refused contact list never blocks adding people: the system picker opens instead (owner decision 2026-11-14).
    LaunchedEffect(state.pickerFallback) {
        if (state.pickerFallback) {
            viewModel.onPickerFallbackShown()
            if (capabilities.hasContactPicker) pickContact()
        }
    }
    OnboardingContent(
        state = state,
        capabilities = capabilities,
        onShowContacts = requestContactList,
        onPickContacts = pickContact,
        onAllowNotifications = requestNotifications,
        onExploreSample = viewModel::onExploreWithSample,
        onFinish = viewModel::onFinish,
    )
    if (state.contactListOpen) {
        // A dialog, so the system back gesture closes the list instead of leaving onboarding.
        Dialog(
            onDismissRequest = { viewModel.onContactListClosed(0) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                contactList(viewModel::onContactListClosed)
            }
        }
    }
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    capabilities: PlatformCapabilities,
    onShowContacts: () -> Unit,
    onAllowNotifications: () -> Unit,
    onExploreSample: () -> Unit,
    onFinish: () -> Unit,
    onPickContacts: () -> Unit = {},
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
                        TextButton(onClick = { scope.launch { pager.animateScrollToPage(PAGES.size) } }) { Text(stringResource(Res.string.skip)) }
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
                    SetupPage(state, capabilities, onShowContacts, onAllowNotifications, onExploreSample, onPickContacts)
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
                    ) { Text(if (last) startLabel(state) else stringResource(Res.string.next), style = MaterialTheme.typography.titleMedium) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun startLabel(state: OnboardingUiState) = stringResource(if (state.hasPeople) Res.string.lets_go else Res.string.start_empty)

@Composable
private fun IntroPage(page: Page) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OrbitIllustration(page.main, page.satellites)
        Spacer(Modifier.height(28.dp))
        Text(stringResource(page.title), style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(page.body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SetupPage(
    state: OnboardingUiState,
    capabilities: PlatformCapabilities,
    onShowContacts: () -> Unit,
    onAllowNotifications: () -> Unit,
    onExploreSample: () -> Unit,
    onPickContacts: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(Res.string.setup_title), style = MaterialTheme.typography.displaySmall)
        Text(
            stringResource(Res.string.setup_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        if (capabilities.hasContactList) {
            // iOS and the Android sideload build: the contacts with checkboxes, the picker as the alternative (D2).
            ContactsPrimer(state, onShowContacts, onPickContacts)
        } else if (capabilities.hasContactPicker) {
            // Play build: no address-book access, people are picked one by one (the app only sees those).
            SetupItem(
                icon = Icons.Rounded.Contacts,
                title = stringResource(Res.string.pick_title),
                body = if (state.contactsPicked > 0) {
                    pluralStringResource(Res.plurals.contacts_picked, state.contactsPicked, state.contactsPicked)
                } else {
                    stringResource(if (capabilities.hasContactWriteBack) Res.string.pick_body_write else Res.string.pick_body)
                },
                done = false,
                action = stringResource(Res.string.pick),
                onClick = onPickContacts,
            )
        }
        if (capabilities.hasBackgroundNotifications) {
            SetupItem(
                icon = Icons.Rounded.Notifications,
                title = stringResource(Res.string.notifications_title),
                body = when (state.notificationsGranted) {
                    true -> stringResource(Res.string.notifications_on)
                    false -> stringResource(Res.string.notifications_off)
                    null -> stringResource(Res.string.notifications_body)
                },
                done = state.notificationsGranted == true,
                action = stringResource(Res.string.allow),
                onClick = onAllowNotifications,
            )
        }
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(Res.string.looking_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(Res.string.looking_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onExploreSample, enabled = !state.isWorking) {
                    Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.explore_sample))
                }
            }
        }
        ListItem(
            leadingContent = { Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.secondary) },
            headlineContent = { Text(stringResource(Res.string.private_title), style = MaterialTheme.typography.titleSmall) },
            supportingContent = { Text(stringResource(Res.string.private_body)) },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f)),
        )
    }
}

@Composable
private fun ContactsPrimer(state: OnboardingUiState, onShowContacts: () -> Unit, onPickContacts: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Diversity3, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Text(stringResource(Res.string.primer_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                stringResource(if (state.contactListDenied) Res.string.primer_denied else Res.string.primer_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (state.contactsPicked > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pluralStringResource(Res.plurals.contacts_picked, state.contactsPicked, state.contactsPicked),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(Res.string.primer_on_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onShowContacts, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(Res.string.primer_show))
            }
            TextButton(onClick = onPickContacts) { Text(stringResource(Res.string.primer_pick_instead)) }
        }
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
                    isDone -> Icon(Icons.Rounded.Check, stringResource(Res.string.done), tint = MaterialTheme.colorScheme.primary)
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
