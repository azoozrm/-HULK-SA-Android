# Release gates

| Gate | PASS requirement | Failure state |
|---|---|---|
| Source governance | canonical hashes and logo hashes match | FAIL |
| Build | clean compile/unit/lint/debug/release/R8 | FAIL |
| Runtime endpoint | compiled APK/AAB contains only `http://3162356.xyz:8080`; CONFIG_URL empty | FAIL |
| Package | `sa.hulksa.player`, expected version | FAIL |
| Signing | approved certificate and required schemes | FAIL |
| ABI | approved ABI/ELF set in APK/AAB | FAIL |
| Bundle | bundletool validation, signed split generation/install | FAIL |
| Upgrade | same package/certificate, higher code, physical or emulator installer evidence | FAIL |
| Compatibility | every expected device artifact present, no P0/P1 product finding | FAIL/BLOCKED |
| Visual | approved SHA baseline, geometry and diff within threshold | FAIL/BLOCKED |
| Downloads | positive bytes, growth, resume offset, final integrity, no duplicate writer | FAIL |
| Playback | protected real smoke or explicitly blocked | FAIL/BLOCKED |
| Physical OEM/ARM | complete protected physical evidence | BLOCKED |
| Performance | physical budget when available; emulator advisory only | BLOCKED if required for RC |

`quality-release.yml` runs only on the official branch and requires explicit baseline, physical, and
protected smoke run IDs. Signing secrets remain in the protected environment. The workflow does
not generate a new key. No workflow merges a PR.

Release recommendations are exactly: `PASS`, `PASS WITH WARNINGS`, `FAIL`, `BLOCKED`, or
`NOT VERIFIED`.

