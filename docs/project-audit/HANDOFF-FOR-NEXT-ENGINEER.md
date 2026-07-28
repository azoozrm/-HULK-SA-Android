# HULK SA Android — تسليم المهندس التالي

تاريخ التحديث: 2026-07-28 UTC

## الملخص التنفيذي

الإصدار التحضيري الحالي هو `0.9.3.18` برقم بناء `62`. الفرع الرسمي هو `phase-3-v0.9.3.0-adaptive-foundation` وHEAD الحالي بعد دمج PR #51 هو:

`75853490d0fd9d7a0ed523eb30133288246094ba`

لا تستخدم `main` كفرع تنفيذ رسمي. لا تبدأ من الصفر ولا تعيد التصميم.

Compatibility Lab Run #31 شُغّل على 9 profiles و133 حالة. بعد مراجعة `summary.json` والصور وFocus traces:

- Phone landscape navigation defects القديمة أغلقت.
- TV Search focus trap أُغلق على 720p و1080p و4K.
- Confirmed Product Critical = **0**.
- Crash/ANR المؤكد = **0/0**.
- Infrastructure errors النهائية = **0**.
- Android TV 720p احتاج retry عابر ثم أنتج 7/7 حالات.
- Raw report سجل حالتين Critical في TV 1080p/Movies، لكن لقطة الدليل كانت Android TV Launcher مع نافذة Google TV Shop؛ التصنيف الصحيح false positive وليس عيب Product.

المشروع ما زال غير جاهز لـv1.0 لأن canonical source وlint والتوقيع والتثبيت وproduction E2E والأجهزة الفعلية لم تُغلق.

## الهوية الرسمية

- Repository: `azoozrm/-HULK-SA-Android`.
- Official branch: `phase-3-v0.9.3.0-adaptive-foundation`.
- Current HEAD: `75853490d0fd9d7a0ed523eb30133288246094ba`.
- versionName: `0.9.3.18`.
- versionCode: `62`.
- Debug suffix: `-beta`.
- applicationId لم يتغير.
- لا يوجد Release tag معتمد لـv0.9.3.18.

## ما تم إنجازه بعد التدقيق الأول

### Compatibility Lab foundation

- AndroidJUnitRunner وAndroidX/Compose UI Test foundation.
- Launcher وLeanback وMainActivity وTvMainActivity launch coverage.
- DPAD/input checks.
- PR-triggered Compatibility Lab.
- 9 emulator profiles مع Screenshots/XML/Logs/Focus traces.
- Generated Source Snapshot workflow.

### PR #50

- إصلاح phone landscape device classification.
- إبقاء Phone Landscape ضمن Mobile/Top Bar.
- تقوية Mobile navigation و48dp minimum targets.
- معالجة Downloads/Settings reachability.
- TV/Home safe-area improvements.
- device-class/rail correction.
- v0.9.3.18 / build 62.

### PR #51

- إصلاح TV Search النهائي.
- Search field على TV يبدأ في navigation read-only mode.
- edit mode يبدأ فقط بتفاعل صريح.
- `DPAD_DOWN` يصل إلى أول نتيجة بدل احتجاز التركيز داخل IME.
- Run #31 أثبت Search focus PASS على 720p/1080p/4K.

## أحدث أدلة CI

### Compatibility Lab Run #31

- Run ID: `30377208398`.
- Tested head: `2af1356cd89bdd2d0f0cb7384791d8e8dfdf6449`.
- Conclusion: `SUCCESS`.
- Build/reconstruction/unit/APK: PASS.
- Profiles: 9.
- Cases: 133.
- Warnings: 263.
- Raw Critical: 2 false positives من لقطة Launcher واحدة.
- Confirmed Product Critical: 0.

### Generated Source Snapshot Run #8

- Conclusion: `SUCCESS`.
- استخدمه لفحص السورس المولد، ولا تخمن مواقع أو أشكال الدوال داخل Scripts.

## الحالة الحالية للمراحل

| المرحلة | الحالة |
|---|---|
| Build/reconstruction | `PARTIAL / ADVANCED` |
| Canonical source | `NOT IMPLEMENTED` |
| Gradle Wrapper 8.13 | `NOT IMPLEMENTED` |
| lint clean على HEAD الحالي | `NOT VERIFIED` |
| Responsive compatibility criticals | `CLOSED` ضمن المختبر |
| Signed Release | `NOT IMPLEMENTED / NOT VERIFIED` |
| Install/upgrade | `NOT VERIFIED` |
| Production E2E | `NOT IMPLEMENTED` |
| Physical ARM/OEM/API 23 | `NOT VERIFIED` |
| Performance/Macrobenchmark | `NOT VERIFIED` |
| v1.0 | `NOT STARTED` |

## المشاكل المفتوحة الأعلى أولوية

### P0

1. لا signed Release APK/AAB من HEAD الحالي.
2. certificate parity وupgrade path غير مثبتين.

### P1

1. السورس النهائي مولد من ZIP + Scripts وليس canonical Gradle project.
2. Gradle Wrapper مفقود.
3. لا Workflow حاكم مباشر واحد.
4. `lintDebug` لم يُعد تشغيله على HEAD الحالي؛ الخطأ التاريخي Media3 قد يبقى.
5. Compatibility classifier يخلط Launcher/System windows مع Product.
6. لا authenticated production E2E.
7. Downloads process-bound بلا Worker/Foreground Service.
8. cleartext/credential transport risk يحتاج تحقق آمن.
9. signed/minified Release runtime غير مختبر.

## الخطوة التالية الوحيدة

أنشئ PR واحدًا بلا تغيير سلوك لـ**canonical source governance**:

1. أعد تكوين HEAD الحالي.
2. ثبّت الناتج كمشروع Gradle مباشر في Git.
3. أضف Gradle Wrapper 8.13.
4. أضف Workflow واحدًا يشغل clean/lint/unit/debug/release/R8/ABI مباشرة من checkout.
5. أثبت parity بين reconstruction وcanonical output.
6. أبقِ ZIP والـScripts والتاريخ مؤقتًا.
7. لا تحذف reconstruction history قبل قبول parity.

بعد قبول هذا PR:

1. lint clean.
2. protected signing.
3. signed APK/AAB + certificate verification.
4. clean install وupgrade.
5. production E2E.
6. physical ARM/OEM/API 23.
7. performance/screenshot/process/network/storage qualification.
8. Release Candidate ثم v1.0.

## قواعد لا يجوز كسرها

- لا تبدأ المشروع من الصفر.
- لا تعيد تصميم المنجز.
- لا تستخدم `main` بدل الفرع الرسمي.
- لا تعتمد على أسماء Commits أو Scripts؛ افحص السورس المولد والـArtifacts.
- لا تعتبر Workflow أخضر Product PASS دون قراءة النتائج.
- لا تصلح Warning heuristic بلا دليل بصري/وظيفي.
- لا تعرض endpoint أو credentials أو signing materials.
- لا تدّع نجاح Release install/E2E/performance/OEM قبل تشغيله.
- لا تغير applicationId أو signing key أو upgrade path دون قرار صريح.
- لا تنشر Release عام دون قرار صريح.
- لا تبدأ Feature كبيرة قبل v1.0.

## الملفات التي يجب قراءتها

1. `PROJECT-STATE.md`.
2. `COMPATIBILITY-RESULTS.md`.
3. `KNOWN-ISSUES.md`.
4. `ROADMAP-TO-V1.md`.
5. `BUILD-AND-RELEASE-AUDIT.md`.
6. `ARCHITECTURE-AUDIT.md`.
7. `TEST-STRATEGY.md`.
8. `project-state.json`.
