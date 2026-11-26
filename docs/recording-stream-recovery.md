# Scheduled-recording source recovery

RecordingStream reopens the saved provider entry URL after premature EOF, a
network read failure, or an HTTP failure. It does not reuse the redirect target
and does not periodically replace a healthy connection. This lets an entry URL
issue a fresh signed/tokenized media URL when its previous stream ends.

The output is created once and kept open across source reconnects. Bytes already
written are never replayed by the recorder. Each input is checked for MPEG-TS
sync before it can be written. Playlists and HTML error pages are rejected.
Storage failures are terminal and cannot cause a source retry, file truncation,
or a replacement output file.

Retries back off for 1, 2, 4, 8, and 10 seconds. The retry budget resets after a
connection has delivered at least 18,800 bytes over at least 30 seconds. The
scheduled end time and cancellation still take priority, including during waits.
A recording that cannot recover retains its data and ends Partial/Failed rather
than Completed. Reconnection can lose content during an outage; upstream replay
or overlap is not automatically deduplicated, and no universal token lifetime is
assumed.

RecordingStreamTest uses real local HTTP redirects to verify fresh entry
requests, one output, exact byte preservation, bounded failures, and storage
errors. RecordingLifecycleTest additionally expires its first redirected stream
and verifies that the Android service requests a new lease, reaches its scheduled
end, lists/range-reads the saved file and deletes it successfully.

For a reported ten-minute channel loop, record the exact channel, source/account,
playback path (local versus casting), device and observed clock/timeline. A
changing news clip alone is not proof of network recovery or source replay.
