# EPG and movie remote regression checks

Run both checks when changing guide geometry, remote key dispatch, focus recovery,
or playback controls. A successful build or install is not a behavioral pass.

## Fixed guide cursor

The vertical accent line is a fixed guide cursor, not a moving wall-clock marker.
It sits 4dp inside the program grid, after the 220dp channel column. Neither clock
updates nor horizontal scrolling may change its screen X coordinate. The ruler
and program tiles scroll together underneath it.

On the affected Android TV device:

1. Open the guide and note the line's position relative to the channel column.
2. Browse several programs into the future with Right. Verify the ruler and tiles
   move together while the line stays fixed.
3. Press Down repeatedly, then Up, including fast direction changes. Verify the
   viewport does not jump back to the current wall-clock time or another program
   boundary. Row selection uses the time underneath the fixed cursor.
4. Wait across a clock-minute update and repeat. Verify the cursor remains fixed.
5. Use Left/Right again; horizontal browsing must still work after vertical moves.

## Movie seeking

1. Start a real movie on the same device.
2. Press Right three times while the progress overlay remains visible. Each press
   must advance the playback target by 30 seconds, except at the end of the movie.
3. Press Left twice with the overlay still visible. Each press must rewind 30
   seconds, except at the start of the movie.
4. Confirm playback resumes after seeking and Back returns to the correct library.
5. Press OK to expand the playback button row. Right must move from Play/Pause
   through Audio and Quality to Subtitles without seeking or hiding the buttons.
   Open Subtitles, return, and verify Left moves back through the controls.
6. Close the button row and repeat the consecutive skip test. A progress-only
   overlay must allow repeated skips; an expanded button row must own DPAD focus.

Guide navigation is scoped to LIVE_TV plus EPG_GRID. Movie seeking is scoped to
FULL_SCREEN with library mode or an active VOD resume key. Do not use a guide
navigation condition as a fullscreen VOD guard.

Record the device address, APK hash, observed cursor position and playback times
for each run. Do not describe a device installation alone as a regression pass.

## Multi-day guide

The EPG setting `Guide days ahead` selects 1, 2, 3, or 7 days (default 7).
Past retention is a separate setting. Hold OK on a program > Guide day / time
(also available in the Menu quick panel) selects a date and
hour; Now returns to the current window. Right at the end of a rendering window
pages forward with an hour of overlap. Left at its first program pages backward.
Rendering stays bounded to four hours; the date/time header identifies the window.

Test tomorrow and the last selected date against actual source listings. A blank
future schedule must say No Information, never reuse current/expired listings.
Then repeat the fixed-cursor and movie-seek checks above. Provider coverage can
be shorter than the configured browsing range.

`EpgGuideRangeTest` checks page overlap, navigation bounds, and reaching all four
configured ranges. Normal command: `:app:testBrandedSideloadDebugUnitTest`.
