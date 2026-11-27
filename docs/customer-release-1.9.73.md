# Customer release 1.9.73 / Recorder preview 0.2.0

Published November 27, 2026 (America/Chicago).

- Android app: versionCode 109 / versionName 1.9.73.
- Windows and Linux recorders: preview 0.2.0.
- Customer page: https://greenstreemlabs.com/
- Existing Discord post updated: https://discord.com/channels/1495933593497767996/1508862475984961557/1508893331474878526
- APK immutable artifact commit: 18749e8df2307e563f59dd203c3445b14c01e593.
- Cloudflare greenstreem-downloads version: eff0733e-aced-49dd-b69b-70f1ebee73f9.

## Public artifacts

| File | SHA-256 |
| --- | --- |
| [greenstreem-customer.apk](https://greenstreemlabs.com/downloads/greenstreem-customer.apk) | `0bd2c3d42a072f4879afe8073a3ab5ae196b222c4c248e73fe5343288fa444c9` |
| [greenstreem-recorder-windows.zip](https://greenstreemlabs.com/downloads/greenstreem-recorder-windows.zip) | `3548aadd6d9de3d3667b35a2fe489394ca720d6ebb92826f45d9aedd370de15a` |
| [greenstreem-recorder-linux.tar.gz](https://greenstreemlabs.com/downloads/greenstreem-recorder-linux.tar.gz) | `88f3a8f590c6dfc1a8da6dc52c0dd38ae9bf73e3df265f04bc4c3ff38daa9290` |

All three full public downloads matched their local verified artifacts. The live download page and public updater feed advertise 1.9.73. The feed uses the immutable APK URL; Windows/Linux packages are directly available on customer downloads.

Branded, sideload and customer APK certificates were independently verified against the existing SHA-256 signing identity:
`e2b654b0f189594c60e9e2f4abc24fb82e02d88a8b31243f53249dd550fc9470`.

The tablet was updated to the final published APK without clearing user data. All 45 Android unit tests passed. Recording UI/library/weekly/padding checks passed on the Samsung tablet and TV emulator; group persistence, tablet touch Settings access, TV remote Settings access, visible Back, timed local recording, guide/cast/movie/subtitle/held-Back regressions passed. Windows recorder tests passed 15 checks with the Linux-only permission check skipped; all 16 passed on Linux with the shipped dependencies. Linux desktop/API, headless help, launcher syntax and permissions were checked.

Google Play was not submitted as part of this customer-download release.
