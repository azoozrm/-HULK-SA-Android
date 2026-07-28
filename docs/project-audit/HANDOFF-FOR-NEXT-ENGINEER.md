# HULK SA Android — تسليم المهندس التالي

تاريخ التحديث: 2026-07-28 UTC

## الهوية الرسمية

- Repository: `azoozrm/-HULK-SA-Android`.
- Official branch: `phase-3-v0.9.3.0-adaptive-foundation`.
- `main` ليس فرع التنفيذ الرسمي.
- versionName: `0.9.3.18`.
- versionCode: `62`.
- Debug suffix: `-beta`.
- applicationId لم يتغير.

## الحالة التنفيذية الصحيحة

المشروع يملك canonical Gradle source وWrapper 8.13 منذ PR #22. بعد ذلك تطور canonical source عبر PRs #23–#45، بينما Compatibility Lab بقي يشغّل reconstruction تاريخيًا مختلفًا. PR #53 أغلق هذا split-brain:

- نقل إصلاحات v0.9.3.18 إلى canonical source الحالي دون استبداله بالسورس التاريخي.
- حافظ على signing safeguards وcache/adaptive fixes وDurable WorkManager Downloads.
- جعل Compatibility Lab يختبر canonical checkout نفسه.
- أبقى reconstruction history كاملًا في مسار منفصل.

## أحدث أدلة CI

### Canonical Build Run #89

Run ID: `30381512860`  
Qualified code head: `3088a1520f1204b95ec1cefa66b5bfd633abc165`

- Wrapper validation: PASS.
- canonical manifest: PASS.
- clean/debug compile: PASS.
- Unit Tests: PASS.
- Debug APK/AAB: PASS.
- `lintDebug`: PASS.
- Release APK/AAB + R8: PASS، غير موقع.
- ABI verification: PASS.
- Unsigned build Artifact: موجود.

### Canonical Compatibility Lab Run #47

Run ID: `30381512894`

- 9 profiles.
- 133/133 cases.
- Raw Critical: 0.
- Confirmed Product Critical: 0.
- Warnings: 238.
- Infrastructure errors: 0.
- Crash/ANR confirmed: 0/0.
- Retries: 0.
- كل navigation audits نجحت.
- TV Search focus نجح على 720p/1080p/4K مع 7 unique targets لكل profile.

Run #31 أصبح مرجعًا تاريخيًا فقط لأنه اختبر reconstructed source وليس التطبيق canonical النهائي.

### Signed Release Qualification Run #14

Run ID: `30381513355`

- unsigned fallback build: PASS.
- incomplete signing configuration fails closed: PASS.
- signed production job: SKIPPED على PR event؛ يحتاج manual dispatch وSecrets محمية.

لا تدّع وجود signed v0.9.3.18 artifacts حتى تشغيل المهمة اليدوية بنجاح.

## حالة المراحل

| المرحلة | الحالة |
|---|---|
| canonical source + Wrapper | `COMPLETED` |
| canonical CI | `COMPLETED` |
| lint clean | `COMPLETED` |
| responsive criticals | `COMPLETED` ضمن fixture matrix |
| signing safeguards/preflight | `COMPLETED` |
| signed production artifacts | `NOT VERIFIED` |
| certificate parity | `NOT VERIFIED` |
| clean install/upgrade | `NOT VERIFIED` |
| production E2E | `NOT VERIFIED` |
| physical ARM/OEM/API 23 | `NOT VERIFIED` |
| Macrobenchmark/performance SLA | `NOT VERIFIED` |
| RC/v1.0 | `NOT STARTED` |

## الخطوة التالية الوحيدة

تشغيل Signed Release Qualification يدويًا داخل protected environment مع Secrets الرسمية، ثم:

1. إخراج signed APK/AAB لـ0.9.3.18 / 62.
2. التحقق من applicationId/version/signature schemes/certificate/ABI/hashes.
3. clean install.
4. upgrade من مرجع الاستقرار.
5. signed R8 runtime smoke.

بعد ذلك production E2E، physical ARM/OEM/API 23، process/network/storage، Macrobenchmark/screenshots/accessibility، ثم RC.

## المشاكل المفتوحة الأعلى أولوية

### P0

- signed production APK/AAB غير مثبتين.
- certificate parity وupgrade path غير مثبتين.
- clean install/upgrade غير مختبرين.

### P1

- real login/catalog/playback/download E2E.
- signed/minified runtime.
- physical ARM/OEM/API 23.
- process death/reboot/network/storage qualification.
- Store/privacy/pre-launch.

## تحذيرات Compatibility الحالية

- high_emulator_jank: 133 — advisory فقط.
- text_at_display_edge: 35.
- slow_page_start: 34.
- interactive_overlap: 20.
- possible_text_clipping: 13.
- tv_safe_area: 3.

لا تعدل UI لمجرد Warning heuristic.

## قواعد لا يجوز كسرها

- لا تبدأ من الصفر.
- لا تعيد التصميم.
- لا تستخدم `main` بدل الفرع الرسمي.
- لا تعرض endpoint أو credentials أو signing materials.
- لا تحذف reconstruction history دون قرار صريح.
- لا تعتبر Emulator شهادة ARM/OEM.
- لا تدّع نجاح Signed Release/install/upgrade/E2E/performance قبل تشغيله.
- لا تغير applicationId أو signing key أو upgrade path دون قرار صريح.
- لا تنشر Release عام دون قرار صريح.
- لا تبدأ Feature كبيرة قبل v1.0.

## اقرأ أولًا

1. `PROJECT-STATE.md`.
2. `CANONICAL-SOURCE-PARITY.md`.
3. `COMPATIBILITY-RESULTS.md`.
4. `KNOWN-ISSUES.md`.
5. `ROADMAP-TO-V1.md`.
6. `RELEASE-SIGNING-QUALIFICATION.md`.
7. `project-state.json`.
