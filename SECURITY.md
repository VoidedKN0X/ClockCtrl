# Security policy

Thank you for helping keep ClockCtrl and its users safe.

## Reporting a problem

**Please don't report security problems in a public issue.** Report them privately instead:

1. Go to the [Security tab](https://github.com/VoidedKN0X/ClockCtrl/security) of this repo.
2. Click **Report a vulnerability** and describe what you found, how to reproduce it, and which version of ClockCtrl you used.

Only the maintainer can see the report. I'll reply as soon as I can, work on a fix, and credit you in the changelog if you'd like.

## Supported versions

Only the latest release gets security fixes. Please update before reporting, and check the [changelog](CHANGELOG.md) to see whether it's already fixed.

## What counts

ClockCtrl has no server and no internet permission: everything stays on your phone. So these count:

- Any way for ClockCtrl to send anything off the phone
- Another app being able to change, delete or silence your alarms without the Set alarm permission
- The ringing screen showing anything other than the ringing alarm or timer while the app lock is on
- Ways around the app lock, or protected screens showing up in screenshots when they shouldn't
- Weaknesses in the app lock PIN
- Anything that lets someone run code or read files they shouldn't

## How releases are protected

Every release is built from this repo by GitHub Actions and signed with the same release key. Its SHA-256 certificate fingerprint is:

```
35:31:CF:CD:26:0B:CB:9A:52:51:BA:A3:71:3D:E9:25:35:0A:7C:CA:28:AD:B5:11:CB:7B:75:CD:AF:3E:DE:70
```

The [CtrlF-Droid repo](https://voidedkn0x.github.io/CtrlF-Droid/) only accepts APKs signed with this key.
