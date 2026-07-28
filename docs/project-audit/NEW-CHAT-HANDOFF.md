# HULK SA Android — NEW CHAT HANDOFF

هذا الملف مكتفٍ بذاته. لا تبدأ المشروع من الصفر، ولا تعيد تصميم ما أُنجز، ولا تضف ميزة كبيرة قبل إغلاق بوابات v1.0.

## الهوية المعتمدة

- Repository: `azoozrm/-HULK-SA-Android`.
- Official branch: `phase-3-v0.9.3.0-adaptive-foundation`.
- `main` ليس فرع التنفيذ الرسمي.
- versionName: `0.9.3.18`.
- versionCode: `62`.
- Debug suffix: `-beta`.
- applicationId لم يتغير.

## الحقيقة المعمارية الحالية

Canonical Gradle source وGradle Wrapper 8.13 موجودان منذ PR #22. PRs #23–#45 عدلت canonical source مباشرة، بينما Compatibility Lab القديم بقي يعيد بناء ZIP تاريخي، فحدث split-brain بين التطبيق الفعلي والمختبر.

PR #53 أصلح الحوكمة دون البدء من الصفر:

- نقل إصلاحات PR #50/#51 إلى canonical source الحالي.
- حافظ على الأعمال canonical اللاحقة، ومنها Durable WorkManager Downloads وsigning safeguards.
- جعل Compatibility Lab ينسخ canonical checkout ويحقن `app/src/debug` فقط.
- حفظ ZIP + patch reconstruction باسم `qa/compatibility/prepare-reconstructed-project.sh` للتاريخ والاسترجاع.
- أضاف canonical SHA-256 manifest كبوابة CI.

## أحدث تشغيلات معتمدة

### Canonical Build Run #89

- Run ID: `30381512860`.
- Qualified code head: `3088a1520f1204b95ec1cefa66b5bfd633abc165`.
- clean/debug/unit: PASS.
- `lintDebug`: PASS.
- Release APK/AAB + R8: PASS، غير موقع.
- ABI verification: PASS.
- canonical manifest: PASS.

### Canonical Compatibility Lab Run #47

- Run ID: `30381512894`.
- Profiles: 9.
- Cases: 133/133.
- Critical: 0.
- Warnings: 238.
- Infrastructure: 0.
- Crash/ANR: 0/0.
- Retries: 0.
- Phone Landscape navigation: PASS.
- TV Search focus 720p/1080p/4K: PASS، 7 unique focus targets لكل profile.

Run #31 تاريخي فقط؛ كان يختبر reconstructed source وليس canonical app النهائي.

### Signed Release Qualification Run #14

- Run ID: `30381513355`.
- unsigned fallback: PASS.
- incomplete signing fails closed: PASS.
- signed production job: SKIPPED لأنه يحتاج manual dispatch وSecrets محمية.

لا يوجد حتى الآن signed production APK/AAB مؤهل لـv0.9.3.18.

## الحالة الحالية

### مكتمل

- canonical source مباشر.
- Gradle Wrapper 8.13.
- canonical manifest وCI.
- lint clean.
- Debug/Release/R8/ABI build qualification.
- Compatibility Product Critical = 0.
- Phone Landscape وTV Search fixes.
- signing safeguards/preflight.

### غير متحقق منه

- signed production APK/AAB.
- certificate parity.
- clean install وupgrade.
- signed/minified runtime.
- real login/catalog/playback/download E2E.
- physical ARM/OEM/API 23.
- process death/reboot/network/storage pressure.
- Macrobenchmark/screenshot regression/accessibility.
- Store pre-launch/staged rollout.

## الخطوة التالية

تشغيل `HULK SA Signed Release Qualification` يدويًا داخل protected environment مع Secrets الرسمية، ثم التحقق من:

1. signed APK/AAB.
2. applicationId وversion 62 / 0.9.3.18.
3. signature schemes وcertificate SHA-256.
4. ABI وartifact hashes.
5. clean install.
6. upgrade من مرجع الاستقرار.
7. signed R8 runtime smoke.

بعدها production E2E، physical ARM/OEM/API 23، reliability/performance، ثم RC وv1.0.

## عند الفشل

- افحص Logs.
- حدد السبب الدقيق.
- أصلح داخل PR منطقي واحد.
- أعد التشغيل.
- لا تتوقف عند كل محاولة صغيرة.

## قرارات خطرة تحتاج توقفًا

- حذف reconstruction history.
- تغيير applicationId.
- تغيير signing key أو certificate.
- كسر upgrade path.
- تغيير معماري كبير.
- نشر Release عام.

## قواعد إلزامية

- لا تبدأ من الصفر.
- لا تعيد التصميم.
- لا تستخدم `main` بدل الفرع الرسمي.
- لا تعرض endpoint أو credentials أو signing materials.
- لا تعتبر Workflow أخضر Product PASS دون Artifacts.
- لا تعتبر Emulator شهادة ARM/OEM.
- لا تدّع نجاح Signed Release/install/upgrade/E2E/performance قبل تشغيله.
- لا تبدأ Feature كبيرة قبل v1.0.

## المراجع

- `docs/project-audit/PROJECT-STATE.md`.
- `docs/project-audit/CANONICAL-SOURCE-PARITY.md`.
- `docs/project-audit/COMPATIBILITY-RESULTS.md`.
- `docs/project-audit/KNOWN-ISSUES.md`.
- `docs/project-audit/ROADMAP-TO-V1.md`.
- `docs/project-audit/HANDOFF-FOR-NEXT-ENGINEER.md`.
- `docs/project-audit/project-state.json`.
