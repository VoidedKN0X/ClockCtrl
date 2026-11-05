# Changelog

What changed in each ClockCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ClockCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Project updates (2026-11-05)

### Project

- **Release checks:** from the next release, the release notes list the APK's SHA-256 checksum, and every release APK gets a signed build provenance attestation, so anyone can check with `gh attestation verify` that it was built by this repo's workflow from the tagged commit.
- **Workflow security:** a new zizmor check scans the GitHub Actions workflows on every change to them, and every workflow now starts with no permissions, giving each job only what it needs.
- **Code scanning:** CodeQL scans the code every week and on every app change; results go to the repo's Security tab.
- **Dependency checksums:** a new manual workflow writes the SHA-256 checksums of every library and Gradle plugin the build downloads, ready for the build to check them in the next release.

## v0.1.2 (2026-11-05)

A small security update for the app lock. Alarms, timers, cities and settings are unchanged.

### Security

- **"Forgot PIN?" now asks for your phone's screen lock.** Before, anyone holding your unlocked phone could tap "Forgot PIN?" on the lock screen and turn ClockCtrl's app lock off without knowing the PIN. Now the reset first asks for your phone's own PIN, pattern, password or fingerprint. If you cancel or it fails, nothing is reset. If your phone has no screen lock at all, the reset works as before.

## v0.1.1 (2026-11-05)

A small hardening update. Alarms, timers and settings are unchanged.

### Privacy

- **No debug logging in release builds.** Debug, verbose and info messages from the libraries ClockCtrl uses are now stripped from the app, so nothing can end up in the phone's system log. ClockCtrl's own code already logged nothing.

### Project

- The build now also checks that the Gradle wrapper is an official Gradle release before building.
- `.gitignore` also covers `google-services.json`, so it can't be committed by accident.
- README: every permission in the app is listed with its Android name and what it's for, including the "dynamic receiver" permission AndroidX adds, and the Ctrl series list now has all 28 apps.

## v0.1.0 (2026-11-05)

The first release of ClockCtrl: a private, offline clock app in the Ctrl series, made to replace your phone's clock app on any Android 13+ phone.

### New

- **Alarms** that ring once or on the days you choose, with a label, their own sound or silent, and vibration on or off. Each alarm shows how long until it rings, and the Alarms tab shows the next one.
- **Skip the next ring** of a repeating alarm without turning it off; it repeats as usual after that.
- **Reliable ringing:** alarms ring at alarm volume through Do Not Disturb, over the lock screen, after a restart even before you unlock, and are set again after a time zone or clock change.
- **Ringing screen** with Snooze and Dismiss (the volume keys snooze). A snoozed alarm shows a notification with the time it rings again and a Dismiss button.
- **Missed alarm notification** when an alarm stops ringing on its own after the time set in Settings.
- **Warnings** at the top of Alarms when notifications, full screen notifications or exact alarms are off, with one tap to fix them.
- **World clock:** your local time, date and next alarm, plus cities you add from every time zone Android knows, with today/tomorrow and the time difference.
- **Timers:** type hours, minutes and seconds or tap a preset (1 to 30 minutes), run several at once, pause, resume, add a minute, reset, name and delete them. Running timers show a live countdown in the notification.
- **Stopwatch** with laps, the fastest lap in violet and the slowest in red, and a notification with Pause, Lap and Reset while it runs.
- **Settings:** snooze length, silence after, gradually louder alarms, the first day of the week, timer sound and vibration, and seconds on the clock.
- **Works with other apps:** "Set alarm", "Set timer", dismiss and snooze from other apps and assistants, and the alarm icon in the status bar opens your alarms.
- **App lock** with its own PIN (at least 6 digits) and optional fingerprint unlock; ringing alarms and timers can always be stopped without it. **Hide in screenshots** and **Hide in recent apps**, both off by default.
- **Ctrl / Signal look** in dark and light, following your phone's theme, with layouts for phones, tablets and foldables.
