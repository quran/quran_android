# Download-management checkpoint — 2026-10-04

Implementation includes a separate `feature:downloads` module with focused state/controller/screen/list-item files, thin Navigation 3 host integration, and real English/Arabic XML labels. Inventory uses only canonical native cache entries; completed recordings have a 256 MiB capacity policy without automatic eviction. Failed/canceled repair retains the old file until validated replacement publication.

Reference-counted storage leases protect prepared practice queues. Removal reports in-use instead of deleting their sources. Result notices use a polite live region; individual removal actions have localized reciter/ayah labels. Removal/update failures remain distinguishable from initial inventory loading failures.

Core storage, controller and queue-lifecycle behavior tests were authored during implementation. A native journey checks physical removal, preservation of the other fixture file, reading-progress persistence, and inventory after relaunch, restoring only its exact fixture files and prior preferences. Fixtures are synthetic and are not Quran recordings.

Combined verification passed: 101 shared JVM tests, shared JVM UI compilation, Android app and instrumentation APK assembly (`BUILD SUCCESSFUL in 49s`). The initial combined run caught an old clear-count expectation; boundary assertions now cover clearing on preparation as well as selection and disposal. Independent GPT-6.1 Sol source review cleared all findings, including source unloading before lease release, imported-source generations, cancellation and accessibility notices. Both locales have 170 matching XML resource keys.

Local native validation is not claimed: the existing emulator APK uses a different debug signature. Automatic approval review rejected updating it with the previously generated temporary signing key, citing an identity-bypass risk. No package was uninstalled and no app data was erased. Native validation will run in the normal fresh GitHub CI emulator instead. AI remains deferred.
