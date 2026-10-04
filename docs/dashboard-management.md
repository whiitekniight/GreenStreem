# Dashboard app management (1.9.58 / 94)

The dashboard is a separate private Python/SQLite service at `_private-autorenew/device-dashboard`, served at dashboard.greenstreemlabs.com. Its files remain outside the tracked public app source. Server deployment preserves production environment and pre-existing server differences through targeted patches and backups.

## Connected features

- Customer nicknames are the existing users.name display field. Login usernames and restore codes remain independent. Email is editable and preserved; customer and device search include saved email addresses.
- Devices report versioned capabilities plus an allowlisted settings schema. Older devices show an update requirement and the server rejects unsupported remote fields.
- General/advanced settings use existing app preferences. Quick-panel and channel-option visibility use dedicated allowlisted flags. Credentials and entitlement flags are never exported as settings.
- Hidden channels, custom channel collections and pinned Live TV groups are editable. Custom/pinned groups belong to the active playlist.
- Playlist provisioning supports the app's XC and M3U profiles. Saved profile credentials are not reported to the dashboard. Provisioning/activation restarts the app; acknowledged command payloads are scrubbed.
- Reusable settings profiles, named device selections, portal endpoints, snapshot export/import and multi-device application use admin-only configuration APIs with existing session/CSRF checks.
- Snapshots require matching category IDs/names on all target devices. Use settings profiles across different playlists.
- Sports management edits real provider category visibility; it is not an external league catalog.
- Maintenance mode opens an app screen and pauses underlying playback. Disabling it restores the app. Offline devices apply queued commands after reconnecting.
- Command status distinguishes queued, delivered, acknowledged and error results.

## Product boundaries

Controls reflect GreenStreem Android features, not every iMPlayer feature. Stalker/MAG, Apple profiles, arbitrary remote app-logo replacement, and an external sports/league database are not added. Account security remains server-managed; reports use the existing device-event service rather than new outbound email delivery. Custom collections use the active playlist's channels. Saved customer emails must be entered where they were previously absent.

## Verification

- 14 Android unit tests pass, including remote preference type/range/allowlist rejection.
- Four Python integration tests pass, covering tenant isolation, customer identity/email updates, capability checks, atomic bulk validation and command acknowledgements.
- Branded and standard sideload release builds pass with signing continuity verified.
- Local browser: nickname/email create/search, profile capture/save/apply and custom-group creation.
- Live Fire TV: version 94 install, schema upload, reversible setting commands acknowledged, categories/favorites preserved, maintenance screen displayed and disabled.
- Testing Shield: version 94 installed; live sync blocked by existing hostname DNS resolution failures. No network settings changed.
