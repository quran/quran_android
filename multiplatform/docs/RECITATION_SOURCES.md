# On-demand recitation sources

The application offers three explicitly supported verse-by-verse sets listed on the official [EveryAyah recitation index](https://everyayah.com/recitations_ayat.html):

| Application ID | Reciter | Official folder |
| --- | --- | --- |
| alafasy | Mishary Rashid Alafasy | [Alafasy 128 kbps](https://everyayah.com/data/Alafasy_128kbps/) |
| husary | Mahmoud Khalil Al-Husary | [Husary 128 kbps](https://everyayah.com/data/Husary_128kbps/) |
| sudais | Abdurrahman As-Sudais | [As-Sudais 192 kbps](https://everyayah.com/data/Abdurrahmaan_As-Sudais_192kbps/) |

Catalog reviewed against the official index on 2026-10-03. A live HTTPS Range probe of `001001.mp3` in each of these three folders returned HTTP 206 with `audio/mpeg` and a 4096-byte sample on that date; this establishes limited endpoint availability, not full-corpus or recitation authenticity validation. The supported catalog follows the app's Hafs verse numbering; provider Warsh sets are excluded. Files use three-digit surah plus three-digit ayah (`018075.mp3`), derived exclusively from a validated VerseId. No arbitrary URL or folder can be provided by the user. Basmalah `NNN000` files, whole-surah recordings, and alternate readings are outside this batch.

These are direct public client downloads from EveryAyah over HTTPS, initiated by the user's selected verse and reciter. No downloaded recitation is committed or shipped in this repository. Availability on a public server is not a broad redistribution license; this implementation does not assert one. Verify permission separately before repackaging or redistributing provider recordings. Imported local recordings remain available as a separate source.

## Download and storage behavior

The application-owned HTTP client disables redirects and applies a 60-second request deadline, 10-second connection deadline, and 15-second socket inactivity timeout. Streaming reads enforce a 20 MiB per-file limit even when Content-Length is absent. HTTP errors, HTML and unsupported types, declared/actual size mismatches, truncated bodies, and non-MPEG Layer III signatures are rejected. `application/octet-stream` is accepted only when the bytes pass the same MPEG checks.

Every MPEG Layer III frame boundary is checked through the end of the file, including an optional bounded ID3v2 prefix and a final fixed-length ID3v1 tag; at least two frames are required. Truncated later frames and unknown trailing bytes are rejected. This is a structural sanity check: it does not authenticate Quran recitation content, verify a signed checksum, or guarantee that the complete audio payload decodes correctly or that the provider file contains the entire intended ayah. Native player errors remain visible to the user.

Completed files live under the app's private persistent storage (`filesDir/recitations` on Android, `Library/Application Support/Quran/recitations` on iOS, `~/.quran-study/recitations` on JVM). Each platform publishes a complete temporary file via atomic rename in the same directory and cleans its temporary file in `finally`. Cancellation is propagated and checked before publication; no partial file is published. A process killed during a write may leave an ignored `.part` file, which is never considered a playable cache entry. Cache entries survive normal app restarts and may be deleted through the repository API. Uninstalling the app removes native app storage. Completed canonical recording files have a 256 MiB aggregate capacity policy; no automatic eviction occurs. Existing offline files remain usable if a legacy cache exceeds the limit. Downloads account for the net replacement size and reject a new file that would exceed capacity. Ignored orphan `.part` files and unrelated files are outside this completed-recording inventory; the policy is not a total filesystem quota. The download-management screen lists completed files and allows explicit removal. Prepared playback queues hold reference-counted leases, including not-yet-downloaded selected ayahs, so removal cannot erase their sources. Leases release on queue/source changes, cancellation, failure and disposal. Inventory and deletion serialize with download publication; a slow download may delay these operations.

`cached()` is a synchronous lightweight existence/size lookup used for UI wording, not an integrity guarantee. `download()` performs existing-file structural validation and repairs a damaged file before playback; verified complete files are reused offline with no HTTP request. Android/JVM file work runs on Dispatchers.IO; iOS uses the native background dispatcher for Foundation IO. Callers should use `download()` before passing the URI to the player. `RecitationDataModule.close()` closes its owned HTTP client when the app composition is disposed.

Selected reciter IDs are persisted through SettingsStore and restricted to the trusted catalog; absent or obsolete values fall back to Alafasy. The existing JVM SettingsStore adapter is in-memory, so JVM UI selection persistence requires a persistent SettingsStore implementation; filesystem audio caching on JVM itself is durable. Android SharedPreferences and iOS NSUserDefaults provide native selection persistence.

## Validation evidence

Behavior tests were authored before the repository/catalog implementation. After the combined source batch, the lead ran verification successfully: 64 JVM tests passed (2 model, 11 domain, 42 data, 9 memorization), and JVM UI compilation plus Android application and test APK assembly passed in a 13-second combined run. No observed RED result is claimed. Native runtime checks and iOS CI are pending at this checkpoint. Common tests cover endpoints, offline reuse, damaged/invalid responses, cancellation, and selection persistence across store recreation. Ktor MockEngine tests cover actual bounded streaming with and without Content-Length, errors, redirects, untrusted hosts, and cancellation. JVM filesystem tests cover cache restart, deletion, and canceled publication. Test MPEG frames are synthetic fixtures and contain no Quran recitation.


### Complete-file provider smoke check

On 2026-10-03, one complete `001001.mp3` file from each official folder was fetched over HTTPS into memory, with the same 20 MiB read bound. Each returned HTTP 200 and `audio/mpeg`. A temporary parser equivalent to `Mp3Validation` accepted every frame boundary through the file end:

| Official folder | Bytes | MPEG frames | Leading ID3v2 bytes | Final ID3v1 |
| --- | ---: | ---: | ---: | --- |
| Alafasy_128kbps | 146,830 | 234 | 0 | Yes |
| Husary_128kbps | 82,164 | 196 | 116 | Yes |
| Abdurrahmaan_As-Sudais_192kbps | 75,786 | 118 | 1,807 | No |

No audio files were saved or bundled. This checks compatibility of the implemented frame-boundary rules with these three particular complete provider samples. It does not establish authenticity, audible correctness, all-verse availability, or compatibility of every file in the provider corpus. The earlier partial Range probe alone did not establish these complete-file frame results.
