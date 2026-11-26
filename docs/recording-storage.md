# Recording storage selection

Use an installed Android folder picker when it can grant persistent folder access. When launching it fails or its returned grant cannot be retained, Recordings automatically offers the built-in storage chooser. No file-manager installation or new broad-storage permission is required.

The fallback lists writable, mounted volumes exposed by `Context.getExternalFilesDirs`, plus device-private storage if no primary external directory is available. The user selects Device storage or USB storage. GreenStreem creates its own Recordings directory on that volume, verifies writing, and saves the choice only after success. The chooser explains that removing the app can remove its app-owned recordings. It does not select or format unavailable drives. Existing NAS folders and SAF folder choices are unchanged.

Direct local folders are validated against the app-owned volume roots. Recording filenames cannot escape their selected folder. Scheduling, file creation, shared-library enumeration, range reads, casting size lookup, playback and deletion support the resulting file URIs. Disconnected storage produces an error rather than silently redirecting a recording to another drive.

Verification for 1.9.68: 25 JVM checks passed; emulator instrumentation scheduled and saved a local recording without a SAF grant, enumerated it, read at an offset and deleted it. The failed-picker UI branch was exercised by making the ActivityResult launcher fail and selecting Device storage through the visible dialog; it saved a usable target. The Shield's mounted removable USB volume was exposed to the app and passed a write/delete probe. The customer's Firestick was not directly connected for hardware testing.
