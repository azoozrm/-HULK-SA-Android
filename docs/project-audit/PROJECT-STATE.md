# HULK SA Android — حالة المشروع المدققة

تاريخ التحديث: 2026-07-28 UTC  
المستودع: `azoozrm/-HULK-SA-Android`  
الفرع الرسمي: `phase-3-v0.9.3.0-adaptive-foundation`  
الإصدار التحضيري: `0.9.3.18`، `versionCode 62`، وDebug يضيف `-beta`.

## تحديث إنقاذ الهوست والحجم

Run `30400862864` أنتج APK/AAB موقعين صحيحين تشفيريًا، لكن فحص الـAPK الفعلي أثبت أن `PORTAL_URL` كان `https://hulksa.com/` و`CONFIG_URL` كان فارغًا. هذا يفسر رسالة رفض الاتصال على Galaxy: التطبيق كان يرسل Xtream login إلى موقع الويب.

الـAPK الموقّع مرفوض كنسخة تشغيل. إصلاح الفرع المستقل يثبت هوست HULK التشغيلي صراحة ويضيف fail-closed checks على generated BuildConfig وDEX داخل APK/AAB.

فرق الحجم ليس نقصًا مثبتًا: v0.9.3.17 المرجعي كان Debug بحجم 24,424,419 بايت و11 DEX، بينما v0.9.3.18 كان R8 Release بحجم 3,426,648 بايت وDEX واحد. native libraries والـABI والمكتبات الأساسية باقية. راجع `V09318-HOST-SIZE-RESCUE.md`.

## الملخص التنفيذي

المشروع يملك سورس Gradle canonical مباشر وGradle Wrapper 8.13 منذ PR #22. الخلل الذي كُشف في هذه الجولة لم يكن غياب canonical source، بل **انقسام مسار التنفيذ**: التطبيق canonical استمر بالتطور في PRs #23–#45، بينما Compatibility Lab بقي يعيد بناء ZIP تاريخي وسلسلة patches مختلفة.

PR #53 أغلق هذا الانقسام دون إعادة بناء المشروع أو حذف التاريخ:

- نقل إصلاحات v0.9.3.18 المثبتة إلى السورس canonical الحالي.
- حافظ على أعمال canonical اللاحقة، ومنها signing safeguards وDurable Downloads المبنية على WorkManager.
- جعل Compatibility Lab ينسخ canonical checkout نفسه ويحقن fixtures داخل `app/src/debug` فقط.
- أبقى مسار ZIP + patches باسم `prepare-reconstructed-project.sh` للتدقيق والاسترجاع.
- أضاف manifest SHA-256 للسورس canonical كبوابة CI.

## نتائج البناء الحاكمة

Canonical Build Run #89 — Run ID `30381512860` — على code head المؤهل `3088a1520f1204b95ec1cefa66b5bfd633abc165`:

| البوابة | النتيجة |
|---|---|
| Gradle Wrapper validation | PASS |
| Canonical source manifest | PASS |
| Clean / Debug compile | PASS |
| Unit Tests | PASS |
| Debug APK/AAB | PASS |
| `lintDebug` | **PASS** |
| Release APK/AAB + R8 | PASS، غير موقع |
| ABI verification | PASS |
| Unsigned build Artifact | PASS |

خطأ Media3 lint التاريخي لم يعد مشكلة مفتوحة على السورس canonical الحالي.

## نتائج Compatibility Lab الحاكمة

Compatibility Lab Run #47 — Run ID `30381512894` — بنى وشغّل **السورس canonical نفسه** على 9 profiles:

| المؤشر | النتيجة |
|---|---:|
| Profiles | 9 |
| Cases | 133/133 |
| Confirmed Product Critical | **0** |
| Raw Critical | 0 |
| Warnings | 238 |
| Infrastructure errors | 0 |
| Crash مؤكد | 0 |
| ANR مؤكد | 0 |
| Retries | 0 |

كل اختبارات التنقل نجحت، بما فيها Downloads وSettings في Phone Landscape. TV Search focus نجح على 720p و1080p و4K؛ كل profile سجل 7 أهداف تركيز فريدة وانتقل `DPAD_DOWN` من حقل البحث إلى أول نتيجة.

Run #31 يبقى مرجعًا تاريخيًا لإصلاحات reconstruction، لكنه لم يعد مرجع Product النهائي لأن المختبر وقتها لم يكن يشغل التطبيق canonical.

## التحذيرات الحالية

| النوع | العدد | القرار |
|---|---:|---|
| `high_emulator_jank` | 133 | Advisory فقط؛ Debug Emulator ليس Macrobenchmark |
| `text_at_display_edge` | 35 | يحتاج Screenshot/XML قبل أي تعديل |
| `slow_page_start` | 34 | لا يعادل Startup SLA |
| `interactive_overlap` | 20 | Heuristic يحتاج إثبات |
| `possible_text_clipping` | 13 | Heuristic يحتاج إثبات |
| `tv_safe_area` | 3 | Advisory؛ لا Critical مؤكد |

لا يُعدل UI لمجرد وجود Warning heuristic.

## التوقيع والإصدار

Signed Release Qualification Run ID `30400862864`:

- APK/AAB الموقعان: PASS تشفيريًا.
- package/version/ABI/checksums: PASS.
- certificate SHA-256: PASS.
- runtime host: **FAIL**؛ جُمّع `hulksa.com`.

لذلك توجد signed artifacts، لكنها ليست Production candidate صالحة. لا يوجد حتى الآن signed runtime-correct APK/AAB أو clean install/upgrade qualification.

## حالة المراحل الرسمية

| المرحلة | الحالة الحالية |
|---|---|
| Canonical source + Wrapper | `COMPLETED` |
| Canonical CI مباشر من checkout | `COMPLETED` |
| lint clean | `COMPLETED` |
| Responsive Compatibility Criticals | `COMPLETED` ضمن المختبر |
| Signing safeguards/preflight | `COMPLETED` |
| Signed production APK/AAB | `BUILT / RUNTIME REJECTED` |
| Canonical runtime host guard | `IMPLEMENTED / CI PENDING` |
| Certificate parity + upgrade | `NOT VERIFIED` |
| Production login/catalog/playback/download E2E | `NOT VERIFIED` |
| Physical ARM/OEM/API 23 | `NOT VERIFIED` |
| Performance/Macrobenchmark | `NOT VERIFIED` |
| Release Candidate / v1.0 | `NOT STARTED` |

## الخطوة التالية

المرحلة التالية هي **Runtime-correct Signed Release Qualification** في بيئة GitHub محمية:

1. بناء Debug وRelease بعد حارس الهوست.
2. إثبات `PORTAL_URL` و`CONFIG_URL` من generated BuildConfig وDEX.
3. إخراج APK وAAB موقعين من v0.9.3.18.
4. التحقق من certificate fingerprint وsignature schemes وpackage/version وABI.
5. clean install وlogin على Galaxy.
6. install/login على Xiaomi/Android TV.
7. upgrade من مرجع الاستقرار مع الحفاظ على البيانات والمسار.
8. R8/minified runtime smoke.

بعد ذلك: production E2E، physical ARM/OEM/API 23، process/network/storage tests، Macrobenchmark، ثم Release Candidate.

## قيود إلزامية

- لا تبدأ من الصفر ولا تعيد تصميم المنجز.
- لا تستخدم `main` بدل الفرع الرسمي.
- لا تعرض endpoint أو credentials أو signing materials.
- لا تحذف reconstruction history قبل قرار حوكمة صريح.
- لا تعتبر Emulator شهادة ARM/OEM.
- لا تدّع نجاح Signed Release أو install/upgrade أو production E2E قبل تشغيلها فعليًا.
- لا تبدأ Feature كبيرة قبل v1.0.
