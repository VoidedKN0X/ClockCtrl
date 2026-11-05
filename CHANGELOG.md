# Changelog

What changed in each ClockCtrl release. Every release on the [Releases page](https://github.com/VoidedKN0X/ClockCtrl/releases) also lists the exact changes in that build.

To publish a new version: bump `versionCode` and `versionName` in `app/build.gradle.kts`, add a section here, and push to `main`. Pushes without a version bump only build; they don't make a release.

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
