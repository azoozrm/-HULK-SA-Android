# HULK SA Android — قائمة المشاكل المدققة

تاريخ التحديث: 2026-07-28 UTC  
الإصدار: `0.9.3.18` / build `62`

## P0 — يمنع Release production

| ID | المشكلة | الحالة | شرط الإغلاق |
|---|---|---|---|
| P0-01 | v0.9.3.18 الموقعة جمعت هوست الموقع بدل هوست HULK التشغيلي | `FIX IMPLEMENTED / RUNTIME PENDING` | signed artifacts جديدة + فحص DEX + login فعلي |
| P0-02 | certificate parity وupgrade path غير مثبتين | `NOT VERIFIED` | مقارنة شهادة مرجع الاستقرار واختبار upgrade فعلي |
| P0-03 | clean install وupgrade للـsigned Release غير مختبرين | `NOT VERIFIED` | install/upgrade evidence من APK الموقع |

## P1 — بوابات v1.0

| ID | المشكلة | الحالة | الملاحظات |
|---|---|---|---|
| P1-01 | real login/catalog/playback/download E2E غير منفذ | `OPEN` | Secrets فقط، دون كشف endpoint أو credentials |
| P1-02 | signed/minified R8 runtime غير مختبر | `OPEN` | بعد إنتاج signed Release |
| P1-03 | physical ARM/API 23/OEM qualification غير منفذة | `OPEN` | أجهزة فعلية، لا Emulator x86_64 |
| P1-04 | process death/reboot/network/storage qualification غير مكتملة | `OPEN` | Durable Downloads موجودة، لكن runtime qualification لازمة |
| P1-05 | Compatibility findings gate لا يزال يحتاج سياسة صارمة موثقة | `PARTIAL` | Run #47 نظيف من Critical؛ فعّل البوابة بعد تثبيت classifier policy |
| P1-06 | cleartext/credential transport policy تحتاج تحقق إنتاجي آمن | `OPEN` | لا تكشف عنوان الخدمة أو بيانات الدخول |
| P1-07 | Store/privacy/Data Safety/pre-launch review غير منفذ | `OPEN` | قبل RC |

## P1/P0 المغلقة

| ID | المشكلة السابقة | الحالة | دليل الإغلاق |
|---|---|---|---|
| C-01 | السورس canonical وGradle Wrapper غير موجودين | `CLOSED / CORRECTED` | كانا موجودين منذ PR #22؛ PR #53 أغلق drift مع المختبر |
| C-02 | Compatibility Lab يختبر reconstruction مختلفًا عن التطبيق canonical | `CLOSED` | PR #53 + Run #47 |
| C-03 | `lintDebug` يفشل بخطأ Media3 | `CLOSED` | Canonical Build Run #89: lint PASS |
| C-04 | Phone landscape يخفي Downloads/Settings | `CLOSED` | Canonical Run #47: navigation PASS |
| C-05 | TV Search focus trap على 720p/1080p/4K | `CLOSED` | Canonical Run #47: 7 unique focus targets لكل profile |
| C-06 | Downloads process-bound فقط | `CLOSED / SUPERSEDED` | Durable WorkManager/foreground implementation موجودة في canonical source؛ بقي runtime qualification فقط |
| C-07 | لا signing safeguards أو fail-closed path | `CLOSED` | Signed Release Qualification Run #14 preflight PASS |
| C-08 | فرق حجم 24.4 MB إلى 3.4 MB مشتبه كنقص | `CLOSED / EXPLAINED` | Debug مقابل R8 Release؛ DEX/resources/native/dependency comparison موثق |

## P2 — جودة واعتمادية

| ID | المشكلة | الحالة |
|---|---|---|
| P2-01 | Screenshot regression suite غير مكتملة | `OPEN` |
| P2-02 | Accessibility qualification غير مكتملة | `OPEN` |
| P2-03 | Macrobenchmark/performance SLA غير منفذ | `OPEN` |
| P2-04 | visual warnings تحتاج triage حالة بحالة | `ADVISORY` |
| P2-05 | long-run memory/leak/ANR qualification غير منفذة | `OPEN` |
| P2-06 | HDR/codecs/subtitles/audio-focus coverage غير مكتملة | `OPEN` |
| P2-07 | version/tag/release governance تحتاج RC policy | `OPEN` |

## P3 — صيانة تقنية

| ID | المشكلة | الحالة |
|---|---|---|
| P3-01 | Workflows تاريخية كثيرة وأسماء متباينة | `OPEN`؛ لا تحذف قبل مراجعة الاعتماديات |
| P3-02 | ملفات UI/Player كبيرة | `OPEN`؛ لا refactor واسع قبل v1.0 بلا عيب مثبت |
| P3-03 | compiler deprecations والتحذيرات المقبولة تحتاج سجلًا مستمرًا | `OPEN` |
| P3-04 | README العام يحتاج توسيعًا | `OPEN` |

## Compatibility advisories الحالية

Run #47: 238 warnings و0 Critical.

| النوع | العدد | القرار |
|---|---:|---|
| `high_emulator_jank` | 133 | لا يصلح كبوابة أداء |
| `text_at_display_edge` | 35 | راجع Screenshot/XML |
| `slow_page_start` | 34 | لا يعادل Macrobenchmark |
| `interactive_overlap` | 20 | heuristic فقط |
| `possible_text_clipping` | 13 | heuristic فقط |
| `tv_safe_area` | 3 | لا Critical مؤكد |

لا تنشئ PR بصريًا لمجرد Warning heuristic.

## غير متحقق منه حتى الآن

- signed runtime-correct APK/AAB بعد إصلاح الهوست.
- certificate parity.
- clean install وupgrade.
- real backend/login/catalog/playback/download.
- physical arm64/armeabi-v7a/API 23/OEM.
- process death/reboot/background/network/storage pressure.
- performance SLA وMacrobenchmark.
- Store pre-launch وstaged rollout.

## ترتيب الإغلاق

1. Signed Release Workflow في protected environment.
2. certificate/signature/package/version/ABI verification.
3. clean install وupgrade.
4. signed R8 runtime + production E2E.
5. physical ARM/OEM/API 23.
6. process/network/storage/performance/screenshots/accessibility.
7. Release Candidate ثم v1.0.
