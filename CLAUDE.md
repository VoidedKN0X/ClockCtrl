# ClockCtrl

Clock app (alarms, world clock, timers, stopwatch) in the VKNXWorks Ctrl series. Kotlin, Jetpack Compose, Material 3, manual DI (`AppContainer`), DataStore for settings. Alarms, timers and cities live in `ClockStore` (JSON in device-protected storage) so they ring before the first unlock.

## Layout

- `data/ClockStore.kt`: alarms, timers, stopwatch, cities and alarm settings; `Alarm.next()` computes the next ring.
- `alarm/Scheduler.kt`: AlarmManager (`setAlarmClock` for alarms, exact alarms for timers and snoozes). `alarm/Receivers.kt`: ring, boot/time-change and notification-action receivers, plus the `Alarms`/`Timers`/`Watch` actions.
- `alarm/RingService.kt`: plays and vibrates while ringing (foreground, mediaPlayback). `alarm/AlarmActivity.kt`: the ringing screen over the lock screen. `alarm/Notifications.kt`: channels and notifications.
- `ApiActivity.kt`: SET_ALARM / SET_TIMER / DISMISS_ALARM / SNOOZE_ALARM from other apps (needs the SET_ALARM permission).
- `ui/alarms`, `ui/world`, `ui/timer`: the four tabs (Alarms, Clock, Timer, Stopwatch).

## Hard rules

- **No `INTERNET` permission, ever** (the manifest removes it; CI fails the build if the APK has any network permission).
- **Google-free:** no Google libraries, services or SDKs, including transitive ones (CI checks `releaseRuntimeClasspath`). No WebView. Fonts are bundled.
- **Telemetry-free:** no analytics or crash reporting.
- arm64-v8a only. Every user-facing string goes in `strings.xml` (shared Ctrl strings in `strings_ctrl.xml`).
- Settings open only from the profile picture. No settings tab, menu or gear icon, no You tab.
- Must work as a normal user app on any ROM (Android 15+), not only when built into the system.
- Everything that rings must work in Direct Boot: ring components are `directBootAware` and only touch `ClockStore` (device-protected storage), never DataStore or the app lock.
- The ringing screen must stay usable when the app is locked and when the phone is locked; it shows only the alarm or timer.
- Never lose alarms: "Forgot PIN" and settings resets keep `ClockStore`; every change goes through `Scheduler` so Android's alarms match the list.

## Dependency verification

Gradle checks every downloaded library and plugin against the SHA-256 checksums in `gradle/verification-metadata.xml` (checksums only, no PGP). When a dependency, plugin or Gradle version changes, start the `verification-metadata.yml` workflow (`gh api -X POST repos/VoidedKN0X/ClockCtrl/actions/workflows/verification-metadata.yml/dispatches -f ref=main`), take the file from its log (between the BEGIN/END markers, `base64 -d | gunzip`), check the diff (only Maven Central, Google's Maven and the Gradle plugin portal) and commit it in the same push as the change. Never turn verification off or make it lenient.

## Releasing

- A release is published only when `versionName` in `app/build.gradle.kts` is bumped. Bump `versionCode` by 1 at the same time.
- Tags are `vX.Y.Z` and releases are named "ClockCtrl X.Y.Z". Never add a build number (`-build.N`, "(build N)").
- Use the same version for every change that ships together; README, licence and CI changes don't need a bump.
- Push straight to `main`; no branches or pull requests.

## Changelog

Every new version needs a detailed entry at the top of `CHANGELOG.md`, under a heading for that version (match the existing headings). CI refuses to release without one, and the entry becomes the release notes on GitHub, in Obtainium and in the CtrlF-Droid repo.

- Write for people using the app, not for developers: what's new, what changed, what was fixed.
- Cover every user-visible change in the version, one bullet each, with enough detail to understand it without opening the app.
- Group with `### New`, `### Changed`, `### Fixed` and `### Security` when there's more than a couple of items.
