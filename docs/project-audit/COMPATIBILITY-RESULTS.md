# HULK SA Android — نتائج Compatibility Lab

تشغيل GitHub Actions: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875>
الرأس المختبر: `8db147faea8fae0290bf75d53b4194de2035880f`
تاريخ التشغيل: 2026-07-27 UTC
حالة Workflow infrastructure: `PASS`
حالة التطبيق بعد مراجعة الأدلة: `FAIL`

## ما الذي اختبره المختبر

المختبر يعيد تكوين سورس v0.9.3.17 نفسه، ثم يضيف Activity وfixtures في `app/src/debug` فقط. لا يغير `app/src/main` ولا Business Logic. يفتح الـauthenticated main shell مباشرة ببيانات deterministic؛ لا يختبر Login ولا backend حقيقي.

لكل حالة يلتقط، حسب توفر Android:

- Screenshot PNG.
- UI hierarchy XML.
- Logcat.
- crash/system event slices.
- activity/window state.
- gfxinfo.
- meminfo.

الصفحات:

- Home
- Live
- Movies
- Series
- Search
- Downloads
- Settings

فحوص إضافية:

- navigation reachability.
- D-pad focus trace للتلفزيون.
- out-of-bounds/zero-size.
- edge/safe-area/text-height heuristics.
- overlapping interactive bounds.
- crash/ANR/render signatures.
- emulator startup/gfx/PSS advisories.

## الأجهزة التي شُغّلت فعليًا

هذه ملفات Emulator geometry/profile وليست أجهزة OEM فعلية. اسم Galaxy S24 Ultra يعني دقة وكثافة مشابهة، لا Samsung firmware.

| الجهاز | API/image | الدقة | density | الاتجاه/font | حالات التطبيق الصالحة | النتيجة المدققة |
|---|---|---:|---:|---|---:|---|
| Pixel 4a | API 29 Google APIs x86_64 | 1080×2340 | 440 | P+L، 1.0 و1.30 | 28/28 | `PASS*` |
| Pixel 6 | API 31 Google APIs x86_64 | 1080×2400 | 420 | P+L، 1.0 | 14/14 | `FAIL` |
| Pixel 8 Pro | API 35 Google APIs x86_64 | 1344×2992 | 480 | P+L، 1.0 | 14/14 | `FAIL` |
| Galaxy S24 Ultra profile | API 35 Google APIs x86_64 | 1440×3120 | 560 | P+L، 1.0 | 14/14 | `FAIL` |
| Pixel Tablet | API 35 Google APIs x86_64 | 1600×2560 | 320 | P+L، 1.0 و1.30 | 28/28 | `PASS*` |
| Nexus 9 | API 28 Google APIs x86_64 | 1536×2048 | 320 | P+L، 1.0 | 14/14 | `PASS*` |
| Android TV 720p | API 36 Android TV x86_64 | 1280×720 | 213 | L، 1.0 | 6/7 | `FAIL`; Series = `NOT TESTED` |
| Android TV 1080p | API 36 Android TV x86_64 | 1920×1080 | 320 | L، 1.0 | 7/7 | `FAIL` |
| Android TV 4K | API 36 Android TV x86_64 | 3840×2160 | 640 | L، 1.0 | 7/7 | `FAIL` |

`PASS*` يعني لا يوجد عيب تطبيق حرج مؤكد في هذه الجولة، مع بقاء warnings آلية تحتاج فرزًا. لا يعني اعتماد جهاز فعلي أو performance pass.

الإجمالي الخام: 133 capture.
الإجمالي الصالح كتطبيق: 132.
السيناريو غير الصالح: Android TV 720p / Series، لأن foreground package كان TV Launcher.

## النتيجة الخام مقابل النتيجة المدققة

| القياس | تقرير الأداة الخام | بعد مراجعة الدليل |
|---|---:|---:|
| critical findings | 10 | 8 عيوب تطبيق مؤكدة |
| invalid infrastructure captures | 0 | 1 capture؛ نتج عنه findingان حرجان زائفان |
| warnings | 252 | 252 advisory؛ لم تُعتمد كعيوب مؤكدة جماعيًا |
| crashes | 0 | 0 |
| ANR | 0 | 0 |
| valid app captures | 133 بحسب التقرير | 132 |

سبب الفرق: analyzer لم يتحقق من foreground package في TV 720p/Series. XML يحدد `package="com.google.android.tvlauncher"`، واللقطة تعرض launcher/dialog خاصًا بمتجر Android TV، لا HULK SA.

## العيوب المؤكدة

### 1. تنقل Landscape لا يصل إلى Downloads/Settings

عدد النتائج المؤكدة: 5.

| الجهاز | الصفحة المطلوبة | الدليل |
|---|---|---|
| Pixel 6 | Downloads | العنصر غير موجود في visible hierarchy |
| Pixel 6 | Settings | العنصر غير موجود في visible hierarchy |
| Pixel 8 Pro | Settings | Screenshot بقي على Downloads والـSettings أسفل الشاشة |
| Galaxy S24 Ultra | Downloads | العنصر غير موجود في visible hierarchy |
| Galaxy S24 Ultra | Settings | العنصر غير موجود في visible hierarchy |

التأكيد من السورس:

- `CinematicNavigationRail` يستخدم `Column` غير scrollable.
- هناك 7 destinations بارتفاع 48dp، مع logo وpaddings وspacers.
- ارتفاع بعض هواتف Landscape بالـdp أقل من المطلوب.

ملاحظة جودة الدليل: navigation audit يستخدم swipe أفقي قرب أعلى الشاشة، وفي بعض Pixel 6 evidence سحب notification shade. هذا يلوث لقطة الدليل، لكنه لا يلغي العيب لأن screenshots أخرى والكود/geometry يؤكدانه.

### 2. TV Search Focus trap

عدد النتائج المؤكدة: 3.

| الجهاز | الصفحة | النتيجة |
|---|---|---|
| TV 720p | Search | focus لم ينتقل إلى target ثان |
| TV 1080p | Search | focus لم ينتقل إلى target ثان |
| TV 4K | Search | focus لم ينتقل إلى target ثان |

Screenshots تظهر keyboard التلفزيون فوق النتائج، و`BasicTextField` يبقى focus owner. السورس لا يوفر TV-specific IME dismissal أو explicit down focus إلى أول نتيجة.

## السيناريو غير المختبر فعليًا

Android TV 720p / Series:

- Screenshot: Android TV Launcher + system shop tutorial.
- XML package: `com.google.android.tvlauncher`.
- HULK page marker مفقود.
- لا Crash/ANR للتطبيق يفسر الخروج.
- النتيجة الصحيحة: `INVALID / NOT TESTED`.

لا يجوز تسجيل Series 720p كـPASS أو كعيب تطبيق قبل إعادة تشغيل الحالة مع:

1. إغلاق onboarding/tutorials في صورة emulator.
2. فحص foreground package بعد كل key action.
3. إعادة launch إذا غادر التطبيق.
4. تصنيف launcher/system UI كـinfrastructure.

## التحذيرات الآلية

| code | العدد | التقييم |
|---|---:|---|
| `high_emulator_jank` | 133 | غير موثوق كبوابة؛ كل الحالات تقريبًا تقيس 4–5 frames Debug |
| `text_at_display_edge` | 39 | مختلط؛ بعضها عناصر LazyRow مجاورة مقصودة عند الحافة |
| `slow_page_start` | 27 | cold Activity restart في debug emulator؛ median كل starts 3.7s، p95 7.591s |
| `interactive_overlap` | 24 | يحتاج screenshot/manual semantics review؛ bounds المتداخلة لا تعني دائمًا click conflict |
| `possible_text_clipping` | 23 | heuristic محافظ؛ يحتاج baseline/manual check |
| `tv_safe_area` | 6 | يحتاج مراجعة 720/1080/4K screenshots وoverscan policy |

لا يوجد `render_pipeline_error` أو `high_memory` أو crash/ANR finding.

## الأداء والذاكرة

- Start metrics متاحة في 132 حالة.
- min: 35 ms.
- median: 3,700 ms.
- p95: 7,591 ms.
- max: 8,916 ms.
- PSS صالح في 91/133 حالة.
- median PSS: نحو 116.6 MiB.
- max PSS: 145,437 KiB، نحو 142.0 MiB.

لا تُحول هذه القيم إلى performance SLA:

- Debug build.
- deterministic fixture.
- emulator/KVM/swiftshader.
- Activity force-stop/restart لكل صفحة.
- gfxinfo frame sample صغير.

المطلوب لقرار الأداء: Macrobenchmark على Release/profileable build، startup mode واضح، 10+ iterations، FrameTimingMetric، MemoryUsageMetric، وعتاد ARM فعلي منخفض/متوسط.

## تغطية الصفحات

كل صفحة مخطط لها 19 حالة عبر matrix. أداة التقرير أعطت كل الحالات `WARN` على الأقل لأن تحذير jank ظهر في 133/133، لذلك جدول page coverage الخام لا يملك أي `PASS`. هذا لا يعني أن كل صفحة مكسورة؛ يعني أن status aggregation يعتبر أي advisory = WARN.

القراءة الصحيحة:

- Home/Live/Movies: ملتقطة في كل الحالات المخططة، بلا critical page marker failure.
- Series: 18 capture صالحة، وTV 720p `NOT TESTED`.
- Search: 19 screenshot capture، لكن D-pad flow فشل في TVs الثلاثة.
- Downloads: screenshots موجودة، لكن navigation reachability فشلت على Pixel 6 وGalaxy Landscape.
- Settings: screenshots موجودة، لكن navigation reachability فشلت على Pixel 6/Pixel 8/Galaxy Landscape.

## Artifacts

| Artifact | الرابط | الصلاحية المسجلة |
|---|---|---|
| Aggregate HTML/Markdown/JSON + evidence | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661831141> | حتى 2026-08-26 |
| Lab APK | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661311514> | حتى 2026-08-10 |
| Pixel 4a | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661521853> | حتى 2026-08-10 |
| Pixel 6 | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661518781> | حتى 2026-08-10 |
| Pixel 8 Pro | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661530546> | حتى 2026-08-10 |
| Galaxy S24 Ultra | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661526943> | حتى 2026-08-10 |
| Pixel Tablet | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661591952> | حتى 2026-08-10 |
| Nexus 9 | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661468067> | حتى 2026-08-10 |
| TV 720p | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661780709> | حتى 2026-08-10 |
| TV 1080p | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661825083> | حتى 2026-08-10 |
| TV 4K | <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661814523> | حتى 2026-08-10 |

Aggregate artifact:

- HTML report: 196,058 bytes.
- Markdown report: 97,969 bytes.
- JSON summary: 799,334 bytes.
- 692 evidence files، و697 ملفًا إجماليًا بما في ذلك ملفات التقرير والفهرس.
- artifact digest من GitHub: `sha256:a328cf0053e2aac0eb1440ea8c7956fbcf6b1f28d290d9b3a90de8e6e9f9927d`.

## موثوقية المختبر

نقاط قوة:

- matrix كبير وفعلي على GitHub-hosted KVM.
- نفس سورس الإنتاج قبل debug harness.
- كل جهاز يحتفظ بالأدلة.
- 4K الحقيقي هو 3840×2160.
- retries وblocked-result support.
- 7 tests لأدوات المختبر ناجحة.
- reports HTML/Markdown/JSON وStep Summary.

False positive risks المثبتة:

- TV Launcher capture صُنّف كعيب تطبيق.
- navigation swipe يمكن أن يفتح notification shade.
- jank 100% من عينة 4–5 frames.
- LazyRow edge text قد يكون clipping مقصودًا.
- semantic bounds overlap لا يثبت visual overlap.

False negative risks:

- debug fixtures تتجاوز login/backend/loading/error states.
- لا playback/download network I/O.
- لا real OEM skin/cutout/overscan.
- لا ARM hardware.
- لا signed/minified Release install.
- لا process death/background.
- لا TalkBack/switch access/keyboard hardware.
- API 23–27 و30 و32–34 غير مغطاة.

## بوابة CI الحالية

كل Jobs في run `30287050875` أنهت `success` لأن `enforce_findings` الافتراضي `false`. هذا يعني:

- أخطاء infrastructure تمنع gate.
- عيوب التطبيق تُسجل لكن لا تفشل Push run افتراضيًا.
- Manual dispatch مع `enforce_findings=true` مطلوب لإفشال critical findings.

قبل اعتماد المختبر كبوابة Release:

1. إصلاح foreground-package classification.
2. إصلاح navigation interaction ليتبع semantics/focus لا swipe عند status bar.
3. فصل advisory عن PASS/FAIL.
4. جعل release gate يستخدم `enforce_findings=true`.
5. إضافة rerun للحالة غير الصالحة، لا اعتبار run كاملاً موثوقًا تلقائيًا.
6. إضافة Release/Macrobenchmark/authenticated tiers منفصلة.

## الحكم النهائي

- Infrastructure execution: `PASS`.
- Evidence generation: `PASS`.
- Device coverage: `GOOD FOR EMULATOR UI`, وليست device certification.
- Product result: `FAIL`.
- Release gate reliability: `PARTIAL`.
- Android TV 720p/Series: `NOT TESTED`.
- Crash/ANR result لهذه الجولة: `PASS` ضمن 132 fixture captures فقط.
- Overall Compatibility Lab confidence: `MEDIUM`.
