# PC recording preview — 1.9.71 (107)

Windows application/source: `C:\Users\PooDoo\AndroidStudioProjects\Projects\GreenStreem Recorder`.
This is the customer's own recorder, not hosted recording on the developer's server.

## Customer flow and explanatory text

- PC: choose an existing local, USB or NAS folder. NAS access uses the Windows
  account; open the share and sign in in File Explorer first.
- Android: Recordings → Set up PC recorder → Find my PC recorder. Manual PC
  address is available if the private-network firewall blocks discovery.
- Click Connect a device on the PC and enter the one-use code in Android.
  Pairing selects PC recording for new schedules. Existing device jobs stay intact.
- Hold a guide program → Schedule recording → Record on PC. The confirmation
  explains which equipment stays on, storage, and provider connection use.
- Only after the PC confirms the schedule may Android be off. No Android exact
  alarm permission or Android foreground recording service is needed in PC mode.
- Recordings → PC recorder shows PC schedules, completed files, replay, cancel
  and delete. Local recordings remain in the existing device list.
- Change Recording destination in the PC recorder screen to return to device
  recording. Disconnecting does not cancel existing server jobs.

## Boundaries

One concurrent MPEG-TS recording, maximum 24 hours per job, home network only.
The Windows PC must remain on and signed in, with the recorder running and its
storage available. It prevents automatic sleep; it cannot recover video missed
during shutdown, manual sleep or a network outage. Optional startup is at Windows
sign-in. This release is not a pre-login Windows service or native NAS package.

The LAN protocol uses HTTP with a bearer token and no redirects. Tokens are
encrypted with Android Keystore, hashed on Windows; provider URLs are protected
by Windows DPAPI. Credentials are not included in replay URLs or JSON lists.
HTTP traffic is not encrypted: do not expose the service to the internet.

## Verification

- Windows test suite covers authentication/single-use pairing, persisted jobs and
  tokens, idempotence, overlap rejection, cancel/delete, byte-range replay, missed
  schedules, encrypted provider URLs, source redirect renewal, HTML rejection,
  unavailable storage, and restart preservation.
- Real Samsung tablet found and paired with the packaged Windows executable.
- A 30-second live test was scheduled through the guide; the Android application
  was force-stopped before its start. The PC completed it to an existing NAS
  share, and Android replay showed video after the application was reopened.
- This proves independence from the Android process; the tablet was not physically
  shut down during this test. Long-duration unattended use and Windows reboot /
  sign-in startup still need broader customer testing before public release.
- Branded Android release compiled; Play shared-source bundle is built separately.
  No public feed, customer download page or Play submission is changed here.
