# Shared recordings

Open **Recordings → Shared recordings**.

## USB attached to another GreenStreem device

1. On the device with the USB drive, choose the recording folder under **Recording storage**. Previously granted folders can be selected again without reopening the system picker.
2. Open **Shared recordings → Share this device’s recording folder → Turn on folder sharing**.
3. On another device on the same home network, choose **Find another stick or device**. Select the sharing device and enter its six-digit code once.
4. Select a video to play. Paired connections are remembered. Refresh the list after new recordings finish.

Keep the sharing device powered on, connected to the network, and the drive attached. Sharing runs as a foreground service with a notification and Stop action. Force-stopping the app stops sharing. Devices on isolated guest Wi-Fi cannot reach each other. If automatic discovery is blocked, **Connect by address** accepts the address and port shown on the sharing device.

## NAS folder

Set up the same existing SMB folder in **Recording storage → Network folder settings** on each device, then choose **Use network folder**. **Shared recordings → Open my recording folder (USB / NAS)** lists video files actually in that folder, including files created by other devices. The NAS must remain on; the device that originally recorded a finished file does not need to remain on.

Alternatively, a paired host can share its selected NAS folder without giving its SMB login to the other devices. In this case the host must remain on too.

## Scope and access

- The library is the selected folder only, with no recursive access or automatic folder creation.
- Supported filename extensions: TS, MP4, MKV, M4V, WebM, AVI, MOV, MPG and MPEG. Playback still depends on device codec support.
- Recordings currently active on the browsing/hosting device are excluded. Files being written by unrelated devices or software may appear before completion; wait for them to finish before playing.
- Remote browsing is read-only. Delete and cancel actions stay in the originating device’s existing Recordings screen.
- Pairing codes expire after five minutes; pairing attempts are limited. Saved access can be revoked with **Forget all paired devices**. Existing playback can continue until its next request.
- This is home-network HTTP streaming with paired access, not an internet sharing service. No router port forwarding is configured. SMB passwords, provider URLs, and private scheduling data are never returned to peers.
- Discovery advertises a stable device ID using Android NSD. Saved addresses are refreshed when that ID is rediscovered, including after its listening port changes.
- Seeking uses HTTP byte ranges. At most eight server connections are accepted at once to limit resource use on streaming boxes.

Fire OS hardware-specific background and multicast behavior needs verification on an actual Firestick before claiming Firestick compatibility has been proven.
