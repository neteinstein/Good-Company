package com.neteinstein.goodcompany.feature.friends.ui.screens.family

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.neteinstein.goodcompany.core.domain.model.FriendId
import com.neteinstein.goodcompany.core.domain.model.LinkType
import com.neteinstein.goodcompany.core.ui.components.EmptyState
import com.neteinstein.goodcompany.core.ui.resources.back
import com.neteinstein.goodcompany.feature.friends.resources.Res
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_empty
import com.neteinstein.goodcompany.feature.friends.resources.suggested_family_title
import com.neteinstein.goodcompany.feature.friends.ui.components.KinshipCard
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import com.neteinstein.goodcompany.core.ui.resources.Res as CoreRes

@Composable
fun SuggestedFamilyScreen(
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    viewModel: SuggestedFamilyViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SuggestedFamilyContent(state, onBack, onOpenFriend, viewModel::onConfirm, viewModel::onDismiss)
}

@Composable
fun SuggestedFamilyContent(
    state: SuggestedFamilyUiState,
    onBack: () -> Unit,
    onOpenFriend: (FriendId) -> Unit,
    onConfirm: (FriendId, LinkType) -> Unit,
    onDismiss: (FriendId) -> Unit,
) {
    Scaffold { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                Row(
                    Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(CoreRes.string.back)) }
                    Text(
                        stringResource(Res.string.suggested_family_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).semantics { heading() },
                    )
                }
            }
            if (!state.isLoading && state.proposals.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Rounded.FamilyRestroom,
                        stringResource(Res.string.suggested_family_title),
                        stringResource(Res.string.suggested_family_empty),
                    )
                }
            }
            items(state.proposals, key = { it.person.id.value }) { proposal ->
                val id = proposal.person.id
                KinshipCard(
                    proposal,
                    onConfirm = { onConfirm(id, it) },
                    onDismiss = { onDismiss(id) },
                    modifier = Modifier.padding(horizontal = 16.dp).clickable { onOpenFriend(id) },
                )
            }
        }
    }
}
