package com.neteinstein.goodcompany.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

/**
 * Hook for shared-element transitions without leaking navigation types into feature modules.
 * The app shell provides an implementation per destination (SharedTransitionLayout + AnimatedContentScope);
 * features just tag elements with a key, e.g. `"avatar-<friendId>"`.
 */
val LocalSharedElement = staticCompositionLocalOf<@Composable (key: String) -> Modifier> { { _ -> Modifier } }

@Composable
fun Modifier.sharedElement(key: String?): Modifier = if (key == null) this else this.then(LocalSharedElement.current(key))
