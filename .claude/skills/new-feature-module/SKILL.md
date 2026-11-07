---
name: new-feature-module
description: Scaffold a new :feature:<name> Kotlin Multiplatform module in Good Company following the repo's MVVM/Koin/testing conventions (screen + stateless content + ViewModel + UiState + commonTest), and wire it into settings, composeApp, navigation, Koin and Kover. Use when adding a new screen area/tab or splitting a feature out.
---

# New feature module

Follow these steps exactly. Read AGENTS.md first if you haven't in this session.

1. **Create the module** `feature/<name>/build.gradle.kts`:
   ```kotlin
   plugins {
       alias(libs.plugins.goodcompany.kmp.feature)
   }
   ```
   The convention plugin adds the Android/iOS/wasm targets, Compose, Koin, lifecycle, `:core:domain`, `:core:ui`, and
   `:core:testing` for tests. Don't add versions. If you need a new library, add it to `gradle/libs.versions.toml` first.

2. **Register it**:
   - `settings.gradle.kts`: `include(":feature:<name>")`
   - `composeApp/build.gradle.kts`, in commonMain: `implementation(projects.feature.<name>)`
   - root `build.gradle.kts`, in `dependencies`: `kover(projects.feature.<name>)`

3. **Package layout** under `feature/<name>/src/commonMain/kotlin/com/neteinstein/goodcompany/feature/<name>/ui/`:
   - `screens/<Name>ViewModel.kt`: `data class <Name>UiState(...)` (immutable, with defaults) and
     `class <Name>ViewModel(useCases..., clock: AppClock) : ViewModel()` exposing
     `val state: StateFlow<<Name>UiState>` built with `combine(...).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), <Name>UiState())`.
     UI events are methods (`fun onX(...)`). Use a `message: String?` + `onMessageShown()` for snackbars.
   - `screens/<Name>Screen.kt`: `@Composable fun <Name>Screen(callbacks..., viewModel: <Name>ViewModel = koinViewModel())`
     collects state with `collectAsStateWithLifecycle()` and calls the stateless
     `@Composable fun <Name>Content(state, callbacks...)`.
   - `components/`: feature-private composables. Shared ones go in `:core:ui`.
   - Never depend on another feature module. Navigate through callbacks.

4. **Domain first.** New business rules go in `core/domain/.../usecase/` as a use case (`operator fun invoke`), with
   tests in `core/domain/src/commonTest`. ViewModels call use cases, never repositories.

5. **Koin**: in `composeApp/src/commonMain/.../di/AppModule.kt`, register the use cases (`factoryOf(::...)`) and the
   ViewModel (`viewModelOf(::<Name>ViewModel)`, or `viewModel { (arg: T) -> ... }` for parameters).
   Add `koin.get<<Name>ViewModel>()` to `composeApp/src/commonTest/.../AppModuleTest.kt`.

6. **Navigation**: add a `@Serializable` route in `composeApp/.../ui/navigation/Routes.kt` (and a
   `TopLevelDestination` entry if it's a tab), plus a `composable<Route> { <Name>Screen(...) }` in `AppNavHost.kt`.

7. **Tests**: `feature/<name>/src/commonTest/kotlin/.../<Name>ViewModelTest.kt` using `runViewModelTest { }`,
   `collecting(vm.state)` and the fakes from `:core:testing`.

8. **Verify**:
   ```bash
   ./gradlew :feature:<name>:testDebugUnitTest :composeApp:testDebugUnitTest spotlessApply spotlessCheck
   ./gradlew :feature:<name>:compileKotlinIosArm64 :feature:<name>:compileKotlinWasmJs :androidApp:assembleDebug
   ```
   Commit as `feat(<name>): ...`.
