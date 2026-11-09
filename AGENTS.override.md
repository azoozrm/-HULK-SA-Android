# HULK SA Android — Owner Signed-Package Physical Override

Owner decision: 2026-11-09, Asia/Riyadh. This supersedes the former Preview/Benchmark
persistent-instance and mandatory dual-refresh rules, including root AGENTS.md section 15.
All unrelated governance, Git/merge/signing/release authority and stop rules remain unchanged.

Read the full specific contract before any physical build/install/instrumentation action:
`docs/android-engineering-lab/PHYSICAL-ENGINEERING-INSTANCES.md`.

The only routine update/runtime target on owner devices is the owner's existing final signed
official app, `sa.hulksa.player`. Use the same application ID and installed signer, with a verified
signed release APK from the authorized signing/artifact route. Update in place only with
`adb install -r`, preserving login, profiles, favorites, history and all existing data.

Do not install or refresh Preview, Benchmark, debug/dev, test/instrumentation or other packages
on owner devices. Leave existing secondary packages untouched; this does not authorize uninstall.
A clean-state/different-package test needs separate owner authority and a separate approved surface;
it is not an automatic fallback.

Verify live device/package/version/signer identity, exact source/APK provenance and compatibility
before installation. Never uninstall, clear/wipe, downgrade, change signer or bypass failed checks.
Do not change versions/keys/configuration to force an update. If a suitable signed APK or authorized
route is unavailable, retain the installed copy and report the required refresh BLOCKED.

This selects the physical target; it does not trigger CI/signing/publication or install on a
source-only/read-only task. Honor current task authority and approved surfaces. Audit/review/
diagnosis/documentation alone never rebuilds/reinstalls the app. Captures remain OFF without
current-task authorization. Reachability and installed state must be verified live.
