# Recording improvements — 1.9.73 / recorder 0.2.0

Hold a guide program to choose Record now, Schedule recording, Stop/cancel a
matching recording, Recordings, or Channel options. Recording now starts at the
current time. A schedule stops automatically at the guide end plus the chosen
extra time. Start early and Finish late offer 0–30 minutes; both default to three.

Weekly means the same channel, day and local time slot, using the scheduling
device's time zone. It is not an episode-title search. Daylight-saving changes
preserve the local clock time. Cancelling a weekly occurrence cancels the series
and keeps saved files. One recording at a time is supported; overlap checks cover
upcoming weekly occurrences over a rolling year. A recorder that misses a week
moves the rule to its next future time, rather than recording an expired show.

A red dot marks guide programs selected for scheduled/active recordings. Extra padding does not mark neighboring programs. Marker
updates change only the title/accessibility label of existing cards, preserving
guide position and focus. The main Recordings list includes paired computer jobs
alongside device jobs. It refreshes every five seconds while visible and preserves
the first visible recording and offset. A disconnected computer gets a clear
message; previously loaded rows remain available. Computer replay uses the
recorder API with its saved device token; no separate SMB setup is needed.

Settings pages, group management/sorting, Downloads, recorder setup and Recordings
have a visible Back button. Existing remote Back behavior is preserved.

Windows and Linux use the same recorder API, persisted jobs, cancellation and
stream recovery. Linux has a branded Tk desktop and a headless runner; recordings
go to an existing writable local, USB or mounted NAS directory. The computer must
remain running and connected. Android may be off only after the computer confirms
the schedule. Linux automatic sleep must be disabled by the user. HLS-only
recording remains unsupported. This does not introduce a hosted recording service.

Customer downloads and the in-app updater were published on November 27, 2026. Google Play submissions are a separate release lane.

## Validation

The isolated improvementsUi suite passed on Samsung tablet R5GL65VKQ4K and the
TV emulator: Record now, local and computer weekly schedules with padding,
daylight-saving rollover, cancellation/idempotence, guide markers updated in
place, the combined recordings list, automatic status refresh preserving scroll,
and a visible Back button. These fixtures use a separate app/data folder.

The guideUi regression suite passed on the TV emulator: scrolling header and
clock, fixed cursor, guide refresh, casting retunes, movie seek steps, Subtitles
focus, short Back and held Back to TV Home. Windows recorder tests passed 15
checks with the Linux-only permission check skipped; all 16 passed under Ubuntu
with the shipped dependency versions. Linux desktop startup/API, headless help,
launcher syntax and credential-file permissions were also checked.

The signed branded 1.9.73 APK was installed on the Samsung tablet with existing
user data preserved. Its installed APK SHA-256 matches the release artifact:
`0bd2c3d42a072f4879afe8073a3ab5ae196b222c4c248e73fe5343288fa444c9`.
Customer downloads are live; see customer-release-1.9.73.md for published artifact verification.

The priorityUi regression passed on the tablet (actual touch scrolling/tapping)
and TV emulator (DPAD traversal), including group-row recycling/persistence and
the Settings Back button. The separate recording lifecycle/local-file regression
also passed on the emulator, including a real timed local recording. Test packages
were removed afterward; the tablet retains only its normal updated app.
