# Changelog

What changed in each ClockCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ClockCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

## Unreleased

Built and checked by CI only: nothing here had been run on a phone when it was written.

### Changed

- **The alarm screen no longer waits for the alarm sound to be opened.** When an alarm or timer goes off, ClockCtrl used to open the sound file first and only then get on with showing the ringing screen; a slow sound file (a large one, or storage that is still waking up after a restart) held everything up, and one that never finished opening could freeze the app with no sound at all. The sound file is now opened in the background. The sounds are still tried in the same order (your chosen sound, the phone's alarm sound, the standard alarm sound, the ringtone) and the first one that opens plays, exactly as before; the phone is kept awake the whole time.

### Fixed

- **An alarm can no longer stay silent because its sound won't open.** If no sound is playing 2 seconds after the alarm went off, the built-in beep starts on the alarm volume and keeps going until the sound file is ready (the sound then takes over) or, when it never is, for as long as the alarm rings. As before, the beep also starts straight away when none of the sound files can be opened.
- **If the alarm sound breaks while it is ringing** (for example because Android's media service restarts), the built-in beep takes over instead of the alarm going quiet.

### Security

- **The signing key is no longer available to the build.** Until now the release key and its passwords were present in the same GitHub Actions job that runs Gradle, so any build plugin or library could in principle have read them. The build job now has no secrets at all and produces an unsigned APK. A separate job, which checks out no code and runs no Gradle, signs it with `apksigner`, and then refuses to hand it on unless it is signed by exactly the certificate published in SECURITY.md and README.md and its contents are identical to what the build job made. Builds are signed with the same key and the same signature scheme (v2) as before, so updates install as usual. Nothing in the app itself changed for this.
- **The build checks that the published fingerprint and the enforced one are the same.** If `build.yml`, SECURITY.md and README.md ever disagree about the signing certificate, the build fails.

### Project

- **Builds no longer use a saved Gradle cache.** Ctrl apps had builds fail with "Dependency verification failed ... from repository Gradle Central Plugin Repository", and keep failing until the caches were deleted by hand. The cause, reproduced on DateCtrl: with a restored cache Gradle still fetches the plugin files from Maven Central on every build; when Maven Central can't be reached for a moment, Gradle takes the Kotlin plugin "marker" file from the Gradle plugin portal instead, the portal serves a different file under the same name, verification rightly refuses it, and the failed run then saved that state as the cache for every later run. Now every build and the code scan start from a clean Gradle home, and the plugin portal is no longer a plugin source at all (`settings.gradle.kts`): everything the build needs is on Maven Central and Google's Maven. Dependency verification itself is unchanged and as strict as before.
- The new build-and-sign path has run for ordinary builds only. ClockCtrl's release job (publishing) has not run with it yet; the next real release is its first use here.

## v0.2.1 (2026-11-20)

Built and checked by CI only: nothing in this release had been run on a phone when it was published.

### Fixed

- **The phone is now kept awake while an alarm or timer rings.** ClockCtrl already had Android's "prevent phone from sleeping" permission for this, but never used it, so with the screen off the phone could doze off while ringing: the sound growing louder, the built-in beep that plays when no sound file can be opened and the "stop after so many minutes" limit all depend on the phone staying awake. ClockCtrl now holds a wake lock from the moment the alarm goes off (including the short moment before the ringing starts) until you snooze or stop it or the ring time runs out. Android releases it by itself one minute after the ring time limit at the latest, so it can never be left on.

### Changed

- **Smoother stopwatch.** While the stopwatch runs, only the running time is redrawn 25 times a second; the lap list and the buttons are left alone, and the fastest and slowest lap are worked out when a lap is added instead of on every tick. With many laps this saves a lot of work and battery.
- **Stopwatch buttons respond at once.** Start, Pause, Lap and Reset on the Stopwatch tab no longer wait for the phone's storage before the screen reacts: the change shows immediately and is saved a moment later in the background. Alarms and timers, and the stopwatch buttons in the notification, are still saved straight away as before, and a background save always writes the newest data, so it can never undo a later change to an alarm or timer.
- **The Timer tab** no longer re-sorts the timer list four times a second.
- **Add city opens without a stutter.** The list of some 400 cities and their time zone names is put together in the background; it appears a moment after the screen opens.
- **Opens with your settings already in place.** ClockCtrl now waits a few milliseconds (half a second at most) for your saved settings before it draws its first screen, so the clock no longer shows for a moment without seconds, or with the wrong first day of the week, and then changes. The ringing screen is not affected and shows at once, as before.

### Privacy

- **"Hide in screenshots" is on from the very first frame.** The app's window now starts hidden from screenshots and screen recordings, and that is lifted only once your saved settings say the switch is off. Before, the protection was switched on a few frames after the app opened.

## Project updates (2026-11-19)

### Project

- **The weekly code scan no longer fails at random.** CodeQL has to watch the app being compiled, but its build could take the compiled code from Gradle's build cache instead, and then the scan stopped with "CodeQL detected code written in Java/Kotlin but this run didn't build any of it" (and GitHub mailed a failed run). The scan's build now runs with `--no-build-cache`, so the code is always compiled. No app changes, so no new version.

## v0.2.0 (2026-11-13)

Two new ways to wake up, both set per alarm under **Waking up** when you add or change an alarm.

### New

- **Sunrise alarm.** Choose 5, 10, 15 or 30 minutes and, that long before the alarm, ClockCtrl shows a full-screen page over the lock screen that slowly turns from a deep warm red through orange to bright white while the screen brightens. It's silent; at the alarm time the alarm rings as usual and its sound fades in (over at least 30 seconds, even with "Gradually louder" off). If you're using the phone when the sunrise starts, it doesn't take over the screen: a quiet notification shows instead, and tapping it opens the sunrise. The sunrise follows Do Not Disturb the same way alarms do, works before the phone is unlocked after a restart, and stops by itself if you switch the alarm off, skip it, change it or delete it. Already awake? **I'm awake** on the sunrise page (or **Skip this alarm** in the notification) skips that ring; a repeating alarm rings as usual next time.
- **Solve a sum to dismiss.** Choose Easy (like 7 + 12), Medium (like 47 + 36 or 7 × 8) or Hard (like 7 × 14 + 38). When the alarm rings, Dismiss shows the sum with a large number keypad; Snooze is still one tap, and so are the volume keys. After 3 wrong answers you get a new sum, so you're never stuck. The alarm keeps ringing at your alarm volume while you solve it and still goes quiet after your "Silence after" time. TalkBack reads the sum aloud ("What is 7 plus 12?"), your answer and whether it was right. With a sum, the Dismiss buttons on the ringing and snoozed notifications are left out (Snooze stays; you can still switch the alarm off in the app), skipping from the sunrise needs the sum too, and "dismiss alarm" from an assistant or another app shows the alarm screen instead of dismissing it.
- The alarm list shows "Sunrise" and "Sum to dismiss" under alarms that use them.

## v0.1.7 (2026-11-13)

A small security update. Alarms, timers and everything else work as before.

### Security

- **Safer alarms from other apps.** When another app or a voice assistant sets an alarm and asks for a particular sound, ClockCtrl now only accepts a shared sound (such as one from your ringtone list). A file path, web address or malformed value is ignored and the alarm rings with your phone's alarm sound instead, so another app can't make ClockCtrl open its own files or set an alarm with a sound it can't play.
- **Checked build tools.** The build now checks the Gradle download against its published checksum, so a tampered build tool can't slip into a release.

## v0.1.6 (2026-11-12)

### Fixed

- **The alarm's label is readable in the dark theme.** On the screen that shows while an alarm or timer rings, the label under the time was drawn in black, so on the dark background you could hardly see it. Text now takes its colour from the theme everywhere in the app: light on the dark background, dark on the light one.

### Changed

- **Slightly smaller download.** The bundled JetBrains Mono font (numbers, readouts and labels) no longer carries the Cyrillic, Vietnamese and maths-alphabet letters ClockCtrl never shows itself, which takes about 0.1 MB off the download and 0.2 MB off the installed app. Everything looks the same; if a name or a log line shown in that font uses one of those scripts, those letters are now drawn with your phone's own font.

## v0.1.5 (2026-11-05)

ClockCtrl now needs Android 15 or newer.

### Changed

- **Needs Android 15 or newer.** Android 13 no longer gets security updates and Android 14's end early 2027, so ClockCtrl now supports Android 15, 16 and 17 only. On older phones the version you have keeps working, but it won't get updates.

## v0.1.4 (2026-11-05)

Confirmations are readable by accessibility apps again.

### Fixed

- "Can't be undone" confirmations (such as deleting an alarm) can be read again by apps that use accessibility, such as automation apps. 0.1.3 hid them from those apps; now only the lock screen, the PIN screens and "Forgot PIN?" are hidden from them. Other apps' floating windows are still kept off these confirmations, and screen readers such as TalkBack keep working everywhere.

## v0.1.3 (2026-11-05)

A security update for the app lock and for requests from other apps. Nothing changes in how you use ClockCtrl.

### Security

- **No floating windows over the app lock.** While the lock screen, a PIN screen (turning the lock on or off, changing the PIN), "Forgot PIN?" or a delete or reset confirmation is showing, other apps' floating windows (chat heads, bubbles, overlays) are hidden on top of ClockCtrl (Android 12 and newer), so no app can draw over the PIN pad or trick you into tapping a button. They come back as soon as that screen closes, and everywhere else in the app they keep working. The ringing alarm and timer screen is unchanged. This uses a new permission, "Hide other apps' floating windows", which Android grants at install without asking.
- **Lock and PIN screens are hidden from apps that misuse accessibility.** On Android 14 and newer, the same screens are marked sensitive, so only real accessibility tools such as screen readers (TalkBack) can read them, not other apps that use the accessibility API to watch the screen.
- **Stricter requests from other apps.** On Android 16 and newer, other apps can only open ClockCtrl in the ways it offers (the launcher, "Set an alarm", "Set a timer" and "Show alarms" from other apps and assistants). A request aimed directly at a part of ClockCtrl that doesn't accept it is now refused by Android.

### Project

- **Check a download:** the release notes now list the APK's SHA-256 checksum, and the APK has a signed build provenance attestation: `gh attestation verify ClockCtrl-v0.1.3.apk -R VoidedKN0X/ClockCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate fingerprint in the README still applies.
- **Checked dependencies:** the build now checks every library and Gradle plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and stops if one doesn't match.
- README: the new permission is in the permissions table, with a "Check a download" note and how dependency verification works.

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
