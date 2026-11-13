# ClockCtrl

**A private, offline clock app: alarms, world clock, timers and a stopwatch.**

[![Latest release](https://img.shields.io/github/v/release/VoidedKN0X/ClockCtrl?label=download)](https://github.com/VoidedKN0X/ClockCtrl/releases/latest) ![Android 15+](https://img.shields.io/badge/Android-15%2B-3DDC84?logo=android&logoColor=white) [![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-blue)](LICENSE) ![Telemetry-free](https://img.shields.io/badge/telemetry-none-brightgreen) ![Offline](https://img.shields.io/badge/internet-none-brightgreen) [![Sponsor](https://img.shields.io/badge/sponsor-GitHub-ea4aaa?logo=githubsponsors&logoColor=white)](https://github.com/sponsors/VoidedKN0X)

ClockCtrl replaces your phone's clock app: alarms that ring reliably (also in Do Not Disturb and before the phone is unlocked after a restart), a world clock, several timers at once and a stopwatch with laps. It never goes online.

Part of the [Ctrl series](#the-ctrl-series) by VKNXWorks.

<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/VoidedKN0X/ClockCtrl"><img src=".github/assets/badge_obtainium.png" alt="Get it on Obtainium" height="80"></a> <a href="https://voidedkn0x.github.io/CtrlF-Droid/"><img src=".github/assets/badge_ctrlf-droid.png" alt="Get it from our F-Droid repo" height="80"></a>

## Features

- **Works fully offline.** ClockCtrl doesn't have the internet permission and never goes online. Alarms, timers and cities are stored only on this phone
- **Alarms** that repeat on the days you pick or ring once, with a label, their own sound (or silent) and vibration. Skip just the next ring of a repeating alarm, and see how long until each alarm rings
- **Reliable ringing:** alarms use Android's alarm clock scheduling, ring at alarm volume through Do Not Disturb, show over the lock screen, ring after a restart before you unlock, and are set again after a time zone or clock change
- **Ringing screen** with Snooze and Dismiss; the volume keys snooze. Snooze length, how long an alarm rings before it goes quiet, and gradually louder sound are in Settings; an alarm that goes quiet leaves a missed alarm notification
- **World clock:** your local time and date, the next alarm, and any cities you add with the difference to your time
- **Timers:** type a time or tap a preset, run several at once, pause, add a minute, reset and name them. Running timers show a live countdown in the notification with Pause and +1 min
- **Stopwatch** with laps (fastest and slowest marked), kept running in a notification while you use other apps
- **Works with other apps:** "Set alarm" and "Set timer" from other apps and assistants, and the alarm icon in the status bar opens ClockCtrl
- **App lock** with its own PIN and optional fingerprint unlock (ringing alarms and timers can always be stopped), and **Hide in screenshots** / **Hide in recent apps** (both off until you turn them on)
- **Ctrl / Signal look**, shared by every Ctrl app: dark and light, following your phone's dark theme setting, high contrast, one violet accent, Space Grotesk and JetBrains Mono; landscape, tablet and foldable layouts

## Requirements

- Android 15 or newer, on a 64-bit ARM phone (nearly every phone from the last several years)
- Works on any Android ROM as a normal app: stock Android, LineageOS, GrapheneOS, CalyxOS, /e/OS and the phone makers' own systems. It doesn't need root or to be built into the system.
- **Google-free and de-Googled:** no Google Play services, Firebase or other Google libraries, and the app never contacts Google.

## Install

1. Open the [latest release](https://github.com/VoidedKN0X/ClockCtrl/releases/latest) on your phone, scroll down to **Assets** and tap the file that ends in `.apk` (for example `ClockCtrl-v0.1.0.apk`). The "Source code" zip and tar.gz files are not the app; you don't need them.
2. Open it on your phone. Android may ask you to allow installing apps from your browser or file manager.
3. Updates install over the old version. Your settings are kept.

**Automatic updates:** on your phone, tap one of the buttons above.

- **Obtainium:** install [Obtainium](https://github.com/ImranR98/Obtainium) first. The button adds ClockCtrl and installs every new release for you.
- **F-Droid:** adds the [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) to F-Droid, Droid-ify or Neo Store, with every Ctrl app and automatic updates.

If the Obtainium button doesn't open the app, add `https://github.com/VoidedKN0X/ClockCtrl` in Obtainium by hand.

**Use it as your clock app:** Android has no "default clock" setting; ClockCtrl handles "Set alarm" and "Set timer" from other apps, and the alarm icon in the status bar opens it. If another clock app is installed, Android may ask which app to use the first time. Turn off alarms in your old clock app so they don't ring twice. ClockCtrl works as a normal app on any Android 15+ ROM; it doesn't need to be built into the system.

## Getting started

- **Alarms:** tap + to add an alarm, pick the time and days, and save. The switch turns an alarm on and off; tap it to change or delete it.
- **Clock:** tap + to add a city; the bin removes it.
- **Timer:** type a time (it fills in from the right, like a microwave) or tap a preset, then Start. Tap + to add another timer.
- **Stopwatch:** Start, Lap while it runs, Pause, then Reset.
- If ClockCtrl shows a warning at the top of Alarms, tap it: alarms need notifications, full screen notifications and exact alarms to ring on time.

Settings and About are behind your profile picture at the top right of every tab.

## Is it safe to install?

- **Open source.** All of ClockCtrl's code is in this repo, under the GPL-3.0 licence. Anyone can read it.
- **Built by GitHub, not on a personal computer.** Every release on the Releases page is built by [the build workflow](.github/workflows/build.yml) on GitHub Actions, straight from the code in this repo. Each build also checks that the Gradle wrapper is an official Gradle release, that the app has no network permission and that it has no Google libraries.
- **Always signed with the same key.** Android refuses to install an update signed with a different key over ClockCtrl, and the CtrlF-Droid repo only accepts APKs signed with this key. The SHA-256 fingerprint of ClockCtrl's signing certificate is:

  ```
  35:31:CF:CD:26:0B:CB:9A:52:51:BA:A3:71:3D:E9:25:35:0A:7C:CA:28:AD:B5:11:CB:7B:75:CD:AF:3E:DE:70
  ```

  Advanced users can check it with [AppVerifier](https://github.com/soupslurpr/AppVerifier).
- **Check a download.** Every release lists the APK's SHA-256 in its release notes, so you can compare it with `sha256sum ClockCtrl-vX.Y.Z.apk`. Each release APK also has a signed build provenance attestation: with the [GitHub CLI](https://cli.github.com/), `gh attestation verify ClockCtrl-vX.Y.Z.apk -R VoidedKN0X/ClockCtrl` proves it was built by this repo's build workflow from the tagged commit. The signing certificate check above still applies.
- **Checked dependencies and workflows.** Every library and Gradle plugin the build downloads is checked against the SHA-256 in [`gradle/verification-metadata.xml`](gradle/verification-metadata.xml), so a changed download fails the build. The workflows are checked with [zizmor](https://docs.zizmor.sh/), each job gets only the permissions it needs, and every action is pinned to a commit. [CodeQL](https://codeql.github.com/) scans the code every week.

**Permissions.** ClockCtrl has **no internet permission**: it can't go online at all. It asks only for what it needs, when it needs it:

| Permission | What ClockCtrl uses it for |
| --- | --- |
| Alarms and reminders (`USE_EXACT_ALARM`) | Ringing alarms and timers at the exact time. |
| Run at startup (`RECEIVE_BOOT_COMPLETED`) | Setting your alarms again after the phone restarts. |
| Notifications (`POST_NOTIFICATIONS`) | Running timers, the stopwatch, snoozed and missed alarms, and the ringing alarm. |
| Full-screen alerts (`USE_FULL_SCREEN_INTENT`) | Showing the ringing alarm or timer over the lock screen. |
| Foreground service (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`) | Playing the alarm sound until you stop it. |
| Vibration (`VIBRATE`) | Vibrating while an alarm rings, if the alarm has vibration on. |
| Prevent phone from sleeping (`WAKE_LOCK`) | Keeping the phone awake while an alarm or timer rings. |
| Use biometric hardware (`USE_BIOMETRIC`) | Optional fingerprint unlock for the app lock. |
| Hide other apps' floating windows (`HIDE_OVERLAY_WINDOWS`) | Hide other apps' floating windows over the PIN screen (and the app lock's other screens and delete confirmations), so nothing can be drawn over them. Granted at install, no prompt. |

Android also lists a "dynamic receiver" permission (`DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`) that AndroidX adds to every app so only ClockCtrl itself can send it internal broadcasts; it gives the app no access to anything. ClockCtrl has no internet, location, contacts, storage, microphone or phone permissions.

Other apps and assistants can set alarms and timers in ClockCtrl ("Set alarm", "Set timer") only if they hold Android's **Set an alarm** permission, as with any clock app.

See [Privacy](#privacy) for what happens to your data.

## App lock

Turn it on under **Settings > Privacy and security > App lock** (tap your profile picture at the top right, then **Settings**). It's off until you turn it on.

- **Its own app PIN** of at least 6 digits, separate from your phone's screen lock. Very obvious PINs such as 111111 or 123456 are refused.
- **Unlock with fingerprint** (optional) only accepts strong biometrics, checked by the phone's secure hardware. The PIN is still needed after the phone restarts and at least every 72 hours.
- **The PIN is never stored.** It's stretched with 600,000 rounds of PBKDF2 and mixed with a key held in the phone's secure hardware.
- **Wrong PINs:** after 5 tries you wait 30 seconds, doubling up to 15 minutes. Restarting the app or the phone, or changing the clock, doesn't skip the wait.
- **Lock again:** as soon as you leave the app (the default), after 1, 5 or 15 minutes or 1 hour away, or only when the app restarts.
- **Forgot your PIN?** It can't be recovered. The lock screen offers to reset ClockCtrl's own settings and turn the lock off, after you confirm with your phone's own screen lock (PIN, pattern, password or fingerprint), so someone holding your unlocked phone can't simply reset it. On a phone without a screen lock the reset needs no confirmation. Your alarms, timers and cities are kept, so a reset never makes you miss an alarm.
- **Ringing is never locked:** a ringing alarm or timer always shows over the lock and can be snoozed or stopped without the PIN.

**Hide in screenshots** and **Hide in recent apps** (both off by default): the first keeps ClockCtrl out of screenshots and screen recordings, the second shows a blank card in the recent-apps view while screenshots still work. With the app lock on, the recent-apps view always shows a blank card.

## Privacy

**Telemetry-free.** ClockCtrl has no analytics, crash reporting, usage stats or tracking of any kind. It has **no internet permission**, works fully offline and never connects to the internet. Every build checks that the app has no network permission and no Google libraries.

- Alarms, timers, the stopwatch and your cities are stored in one small file in ClockCtrl's own storage on the phone; nothing else is kept.
- World clock times come from the time zones built into Android; no city list is downloaded.
- ClockCtrl is left out of Android's cloud backup and its phone-to-phone transfer.
- The Support, Report a bug, Source code and Licence links on the You page open in your browser, only when you tap them.

## How ClockCtrl is made

ClockCtrl is a one-person project by VoidedKN0X (VKNXWorks). Much of the code is written with the help of AI (Claude, by Anthropic). I decide what the app does, review the changes and use the app myself. Every change is listed in the [changelog](CHANGELOG.md), and you can read every line of code here.

## Build from source

You need [Android Studio](https://developer.android.com/studio), or JDK 17 and the Android SDK.

1. Clone the repo:
   ```bash
   git clone https://github.com/VoidedKN0X/ClockCtrl.git
   cd ClockCtrl
   ```
2. Build the app:
   ```bash
   ./gradlew assembleDebug
   ```
   On Windows, use `gradlew.bat assembleDebug`.
3. The APK is in `app/build/outputs/apk/debug/`. Copy it to your phone and install it.

Or open the folder in Android Studio and press **Run** with your phone connected.

**Dependency verification.** Gradle checks every library and plugin it downloads against the SHA-256 checksums in `gradle/verification-metadata.xml`, and the build fails if one doesn't match or is missing. When you add or update a dependency or plugin, run the **Dependency verification metadata** workflow (Actions tab, *Run workflow*), decode the file from its log (between the BEGIN and END markers: `base64 -d | gunzip`), check the diff and commit it together with the dependency change.

## Support the project

ClockCtrl is free and open source. If it's useful to you, you can support development on [GitHub Sponsors](https://github.com/sponsors/VoidedKN0X). In the app, tap your profile picture: **About** at the bottom of the You page has **Support ClockCtrl** and **Report a bug**, which opens the [issue tracker](https://github.com/VoidedKN0X/ClockCtrl/issues). Please report bugs and ideas with the [bug report and feature request forms](https://github.com/VoidedKN0X/ClockCtrl/issues/new/choose). Starring the repo helps too.

Found a security problem? Please don't open a public issue; see [SECURITY.md](SECURITY.md).

## The Ctrl series

Native Android apps for self-hosted services and everyday tools, all with the same design.

| App | What it's for |
| --- | --- |
| [AdCtrl](https://github.com/VoidedKN0X/AdCtrl) | Control your AdGuard Home DNS server |
| [ArrCtrl](https://github.com/VoidedKN0X/ArrCtrl) | Radarr, Sonarr, Prowlarr, Bazarr, qBittorrent, autobrr and Cleanuparr in one app |
| [BeamCtrl](https://github.com/VoidedKN0X/BeamCtrl) | TV remote for phones with an IR blaster |
| [CallCtrl](https://github.com/VoidedKN0X/CallCtrl) | Phone app: dialer, call history and in-call screen |
| [CameraCtrl](https://github.com/VoidedKN0X/CameraCtrl) | Private camera with RAW (DNG) on every lens |
| [ChatCtrl](https://github.com/VoidedKN0X/ChatCtrl) | Matrix chat client for matrix.org and self-hosted Synapse |
| **ClockCtrl** (this app) | Alarms, timers, stopwatch and world clock |
| [ContactCtrl](https://github.com/VoidedKN0X/ContactCtrl) | Contacts: browse, edit, import and export |
| [CraftyCtrl](https://github.com/VoidedKN0X/CraftyCtrl) | Manage Minecraft servers on Crafty Controller |
| [DateCtrl](https://github.com/VoidedKN0X/DateCtrl) | Calendar for the calendars on your phone and your accounts |
| [FilesCtrl](https://github.com/VoidedKN0X/FilesCtrl) | File manager with NFS, SMB, SFTP, FTP and WebDAV |
| [FrigateCtrl](https://github.com/VoidedKN0X/FrigateCtrl) | Watch and review your Frigate NVR cameras |
| [GalleryCtrl](https://github.com/VoidedKN0X/GalleryCtrl) | Private, offline gallery with an encrypted vault |
| [ImmichCtrl](https://github.com/VoidedKN0X/ImmichCtrl) | Photos and backup for your Immich server |
| [JellyCtrl](https://github.com/VoidedKN0X/JellyCtrl) | Movies, shows, music and Live TV from Jellyfin |
| [LijnCtrl](https://github.com/VoidedKN0X/LijnCtrl) | Live De Lijn bus and tram departures |
| [LumenCtrl](https://github.com/VoidedKN0X/LumenCtrl) | Private, offline photo and video editor |
| [MailCtrl](https://github.com/VoidedKN0X/MailCtrl) | Email for Gmail and Proton Mail |
| [MapsCtrl](https://github.com/VoidedKN0X/MapsCtrl) | Private, offline maps and navigation |
| [MemoCtrl](https://github.com/VoidedKN0X/MemoCtrl) | Notes on your Memos server |
| [NaviCtrl](https://github.com/VoidedKN0X/NaviCtrl) | Music player for Navidrome servers |
| [PaperCtrl](https://github.com/VoidedKN0X/PaperCtrl) | Scan and browse documents on Paperless-ngx |
| [PrintCtrl](https://github.com/VoidedKN0X/PrintCtrl) | Bambu Lab printers through Bambuddy, and Spoolman spools |
| [ProxCtrl](https://github.com/VoidedKN0X/ProxCtrl) | Manage your Proxmox VE server |
| [RecorderCtrl](https://github.com/VoidedKN0X/RecorderCtrl) | Private, offline voice recorder |
| [RideCtrl](https://github.com/VoidedKN0X/RideCtrl) | Offline maintenance, fuel and mod log for your vehicles |
| [SeerrCtrl](https://github.com/VoidedKN0X/SeerrCtrl) | Request movies and shows on Seerr / Jellyseerr |
| [ShutterCtrl](https://github.com/VoidedKN0X/ShutterCtrl) | Remote control and photo transfer for Canon cameras |
| [SoundCtrl](https://github.com/VoidedKN0X/SoundCtrl) | JBL headphones and speakers, without the account |
| [SumCtrl](https://github.com/VoidedKN0X/SumCtrl) | Offline calculator, unit converter and work tools |
| [SynapseCtrl](https://github.com/VoidedKN0X/SynapseCtrl) | Administer your Matrix Synapse server |
| [TalkCtrl](https://github.com/VoidedKN0X/TalkCtrl) | Cardo intercom settings, radio and updates |
| [TapoCtrl](https://github.com/VoidedKN0X/TapoCtrl) | Tapo plugs and bulbs on your home network |
| [TextCtrl](https://github.com/VoidedKN0X/TextCtrl) | Private SMS and MMS messaging |
| [TrueCtrl](https://github.com/VoidedKN0X/TrueCtrl) | Monitor and manage your TrueNAS server |
| [WakeCtrl](https://github.com/VoidedKN0X/WakeCtrl) | Keep the screen awake, per app or while charging |
| [WrtCtrl](https://github.com/VoidedKN0X/WrtCtrl) | Manage your OpenWrt router |

## License

ClockCtrl is licensed under the [GNU General Public License v3.0](LICENSE). © VKNXWorks

Fonts, bundled in the app (nothing is downloaded): [Space Grotesk](https://github.com/floriankarsten/space-grotesk) and [JetBrains Mono](https://github.com/JetBrains/JetBrainsMono), both under the SIL Open Font License 1.1 ([FONT-LICENSE-SpaceGrotesk.txt](FONT-LICENSE-SpaceGrotesk.txt), [FONT-LICENSE-JetBrainsMono.txt](FONT-LICENSE-JetBrainsMono.txt)).
