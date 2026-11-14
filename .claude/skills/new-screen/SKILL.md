---
name: new-screen
description: Add a screen (or a dialog/sheet destination) to an EXISTING Good Company feature module - UiState + ViewModel on use cases, stateless Content, Screen with koinViewModel, sealed message type, @Serializable route, AppNavHost wiring through callbacks, Koin registration, AppModuleTest, a ViewModel test on TestGraph, strings and screenshots. Use for any new route; for a whole new :feature module use new-feature-module instead.
---

# New screen in an existing feature

Worked example: gift lists (`feature/hangouts/.../ui/screens/gifts/`, `GiftListsViewModel` + `GiftListsScreen`, and
`GiftListViewModelTest`). Read it before starting. Package: `com.neteinstein.goodcompany.feature.<name>.ui.screens.<area>`.

## 1. Domain first

The screen calls **use cases only** (plus `AppClock` / `IdGenerator`). Missing logic goes into `core/domain/.../usecase/`
with tests, not into the ViewModel. Group related ones in a `FooUseCases` class when a screen needs many.
If the screen needs new stored data, do the `persistence-change` skill first.

## 2. ViewModel (`FooViewModel.kt`)

```kotlin
sealed interface FooMessage { data class Deleted(val title: String) : FooMessage }

data class FooUiState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = true,
    val message: FooMessage? = null,
    val recentlyDeleted: Item? = null,     // for Undo
)

class FooViewModel(observeFoo: ObserveFooUseCase, private val foo: FooUseCases, clock: AppClock) : ViewModel() {
    private val local = MutableStateFlow(FooUiState())
    val state: StateFlow<FooUiState> = combine(local, observeFoo()) { ui, items -> ui.copy(items = items, isLoading = false) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), local.value)
    fun onDelete(id: String) { viewModelScope.launch { … local.update { it.copy(message = FooMessage.Deleted(…)) } } }
    fun onMessageShown() = local.update { it.copy(message = null) }
}
```
Runtime parameters (an id) are the first constructor arguments. AI parts show only when `settings.usableAiKey != null`
(see `area-ai`).

## 3. Screen (`FooScreen.kt`)

- `@Composable fun FooScreen(onBack: () -> Unit, onOpenX: (String) -> Unit, viewModel: FooViewModel = koinViewModel())`
  (with a parameter: `koinViewModel(key = id) { parametersOf(id) }`, the key so two ids don't share one) collects `state` with `collectAsStateWithLifecycle()`,
  shows `message` in a snackbar (`message.text()`, then `onMessageShown()`), Undo for destructive actions, haptics on the
  primary action, and owns platform launchers (permissions, pickers).
- `@Composable fun FooContent(state: FooUiState, actions: FooActions, modifier: Modifier = Modifier)`: stateless,
  previewable, no logic. Build it from `:core:ui` (`ScreenHeader`, `SectionCard`, `FriendAvatar`, …) and theme tokens.
- `suspend fun FooMessage.text(): String = when (this) { … getString(Res.string.…) }` at the bottom of the file.
- Navigation leaves the screen only through callbacks; no navigation types in the feature module. Shared elements use
  `Modifier.sharedElement("avatar-<id>")`.
- Headers (issue #65): a **tab's** root screen puts `HeaderSettingsButton(onOpenSettings)` (core:ui) in its
  `ScreenHeader` `trailing` (Settings is no longer a tab); a screen **pushed** over the tabs uses
  `ScreenHeader(navigation = { HeaderBackButton(onBack) })`.

## 4. Wiring (`composeApp`)

1. `ui/navigation/Routes.kt`: `@Serializable data object FooRoute` or `data class FooRoute(val id: String)`, with a KDoc
   line saying what it is (and the issue).
2. `ui/navigation/AppNavHost.kt`: `composable<FooRoute> { entry -> val id = entry.toRoute<FooRoute>().id; FooScreen(…) }`
   (a sheet/dialog: `dialog<…>`), and the callers' `onOpenFoo = { navController.navigate(FooRoute(…)) { launchSingleTop = true } }`.
3. `di/AppModule.kt`: use cases, then `viewModelOf(::FooViewModel)` or
   `viewModel { (id: String) -> FooViewModel(id, get(), get()) }`.
4. `composeApp/src/commonTest/.../AppModuleTest.kt`: `assertNotNull(koin.get<FooViewModel>())` (or with `parametersOf`).
5. Web demo/screenshot entry, if useful: `LaunchOptions` (`?tab=…&friend=…`).

## 5. Tests

`feature/<name>/src/commonTest/.../FooViewModelTest.kt`: build a `TestGraph(listOf(friend("ana", "Ana")), settings = …)`,
create the subject from `g.<useCase>` and `g.clock`, drive it inside `runViewModelTest { val state = collecting(vm.state); vm.onX(); assertEquals(…, state.value…) }`,
and assert on state, on the message **type**, and on the fake repositories (`g.friendRepo`, …).

## 6. Finish

Strings in five languages (`add-strings`). `./gradlew spotlessApply testDebugUnitTest koverVerify :androidApp:lintDebug`.
Visible change → refresh screenshots (`capture-screenshots`) and look at them. If the screen belongs to an area with a
skill (`area-*`), follow its rules and add anything new you learned to it.
