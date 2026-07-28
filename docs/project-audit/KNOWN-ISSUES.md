# HULK SA Android — قائمة المشاكل المدققة

تاريخ التحديث: 2026-07-28 UTC  
HEAD: `75853490d0fd9d7a0ed523eb30133288246094ba`  
الإصدار: `0.9.3.18` / build `62`

الحالات المستخدمة:

- `OPEN`: مشكلة مثبتة ومفتوحة.
- `PARTIAL`: عولج جزء منها وبقيت فجوة.
- `CLOSED`: أغلقت بدليل تشغيل مناسب.
- `ADVISORY`: تحذير يحتاج triage ولا يثبت عيبًا وحده.
- `NOT VERIFIED`: لم يُشغل السيناريو المطلوب.

## P0 — يمنع Release production

| ID | المشكلة | الحالة | دليل/شرط الإغلاق |
|---|---|---|---|
| P0-01 | Release APK/AAB غير موقعين ولا يوجد signing path محمي من HEAD الحالي | `OPEN` | GitHub protected environment + Secrets، إخراج signed APK/AAB، التحقق من certificate، install وupgrade من مرجع الاستقرار، دون كشف materials |
| P0-02 | upgrade certificate parity غير مثبت | `NOT VERIFIED` | مقارنة شهادة مرجع الاستقرار بالـRelease الجديد واختبار upgrade فعلي |

## P1 — بوابات v1.0 الأساسية

| ID | المشكلة | الحالة | الملاحظات |
|---|---|---|---|
| P1-01 | السورس النهائي مولّد من ZIP + Scripts وليس Gradle project canonical | `OPEN` | الخطوة التالية المباشرة؛ materialize + Wrapper 8.13 + parity |
| P1-02 | `lintDebug` لم يثبت clean على HEAD الحالي | `OPEN` | الخطأ التاريخي Media3 unstable API في PlayerScreen يجب إعادة تشغيله وإصلاحه إن بقي |
| P1-03 | لا Workflow بناء حاكم واحد يعمل مباشرة من checkout | `OPEN` | مطلوب clean/lint/unit/debug/release/R8/ABI من canonical source |
| P1-04 | Compatibility classifier يحسب launcher contamination كـProduct Critical | `OPEN` | Run #31 سجل حالتين raw في TV 1080p/Movies رغم أن الصورة Launcher؛ أصلح classifier قبل strict gate |
| P1-05 | findings gate غير مفعل افتراضيًا كشرط صارم موثوق | `PARTIAL` | لا يُفعل حتى إصلاح false positives؛ بعدها enforce Product Critical فقط |
| P1-06 | لا authenticated production E2E | `OPEN` | real login/catalog/playback/download دون كشف endpoint أو credentials |
| P1-07 | Downloads process-bound بلا WorkManager/Foreground Service | `OPEN` | process death/reboot/background tests ثم حل معماري مثبت |
| P1-08 | cleartext مسموح عالميًا وقد تمر credentials داخل query/path عند HTTP | `OPEN` | تحقق من policy الفعلية، تقييد cleartext أو توثيق استثناء آمن دون كشف endpoint |
| P1-09 | Signed/minified Release runtime غير مختبر | `OPEN` | بعد signing: install، launch، playback/download smoke، R8 qualification |

## P1 المغلقة في v0.9.3.18

| ID | المشكلة السابقة | الحالة | دليل الإغلاق |
|---|---|---|---|
| P1-C01 | Phone landscape يتحول خطأ إلى Tablet/Rail ويخفي Downloads/Settings | `CLOSED` | PR #50 + Run #31؛ لا Product Critical على phone profiles |
| P1-C02 | TV Search focus trap على 720p/1080p/4K | `CLOSED` | PR #51 + Run #31؛ Search focus `PASS` و7 unique targets لكل TV profile |
| P1-C03 | TV Search يفتح IME مباشرة ويمنع D-pad | `CLOSED` | وضع navigation read-only + edit mode صريح؛ `DPAD_DOWN` يصل لأول نتيجة |

## P2 — جودة واعتمادية

| ID | المشكلة | الحالة |
|---|---|---|
| P2-01 | Home cache لا يدخل `favorites` في invalidation وقد تتقادم recommendations | `OPEN` |
| P2-02 | `UserLibrary.replaceFavorites()` يستخدم synchronous `commit()` | `OPEN` |
| P2-03 | Adaptive sizing يعتمد على `Configuration` بدل window container | `PARTIAL`؛ التصنيف الحرج أصلح لكن الحوكمة المعمارية باقية |
| P2-04 | TV/phone visual warnings تحتاج triage يدوي | `ADVISORY` |
| P2-05 | startup يقرأ JSON/download metadata على main thread | `SUSPECTED / NOT VERIFIED` |
| P2-06 | لا screenshot regression suite | `OPEN` |
| P2-07 | لا process-death/reboot/network/storage test suite | `OPEN` |
| P2-08 | لا physical ARM/API 23/OEM qualification | `OPEN` |
| P2-09 | لا Macrobenchmark أو performance SLA | `OPEN` |
| P2-10 | version/tag/default-branch governance غير مكتملة | `OPEN` |

## P3 — صيانة تقنية

| ID | المشكلة | الحالة |
|---|---|---|
| P3-01 | Workflows كثيرة وأسماء إصدارات متباينة | `OPEN` |
| P3-02 | Gradle Wrapper مفقود من المصدر الرسمي | `OPEN`؛ سيغلق مع canonical PR |
| P3-03 | compiler deprecations وشروط دائمًا true | `OPEN` |
| P3-04 | duplicate payload/icon resources | `OPEN` |
| P3-05 | MainShell/Player/DownloadRepository ملفات ضخمة | `OPEN`؛ لا refactor واسع قبل v1.0 بلا حاجة مثبتة |
| P3-06 | README غير كافٍ | `OPEN` |

## Compatibility advisories الحالية

Run #31: 263 warnings.

| النوع | العدد | القرار |
|---|---:|---|
| `high_emulator_jank` | 133 | لا يصلح كبوابة أداء |
| `text_at_display_edge` | 42 | راجع الصورة وXML قبل التعديل |
| `slow_page_start` | 39 | لا يعادل Macrobenchmark |
| `possible_text_clipping` | 22 | heuristic فقط |
| `interactive_overlap` | 21 | heuristic فقط |
| `tv_safe_area` | 6 | لا Critical مؤكد |

لا تنشئ PR إصلاح بصري لمجرد وجود Warning. يجب إرفاق Screenshot/XML يثبت أثرًا حقيقيًا.

## False positives المثبتة

1. Android TV Launcher/Google TV Shop contamination في Run #31، حالة TV 1080p/Movies؛ أنتجت `page_marker_missing` و`empty_hierarchy`.
2. Android TV Launcher capture التاريخية في TV 720p/Series.
3. notification shade contamination في navigation audit التاريخي.
4. `high_emulator_jank` من عينة Debug Emulator قصيرة.
5. lazy edge/semantic overlap heuristics.

## أشياء ما زالت غير متحقق منها

- ملكية signing key ومسار upgrade certificate الفعلي.
- signed Release install/upgrade.
- real backend/login/catalog.
- real media playback/download.
- physical ARM وOEM وAPI 23.
- HDR/codecs/subtitles/audio focus.
- process death/reboot/background constraints.
- long-run leak/ANR.
- Store pre-launch/Data Safety/privacy.
- staged rollout.

## ترتيب الإغلاق

1. canonical source governance.
2. lint clean وcanonical CI.
3. protected signing + signed install/upgrade.
4. production E2E وR8 runtime.
5. physical ARM/OEM/API 23.
6. performance/screenshot/process/network/storage qualification.
7. Release Candidate ثم v1.0.
