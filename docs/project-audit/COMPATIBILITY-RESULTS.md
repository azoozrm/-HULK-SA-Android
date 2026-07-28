# HULK SA Android — نتائج Compatibility Lab

تاريخ التحديث: 2026-07-28 UTC  
الفرع الرسمي: `phase-3-v0.9.3.0-adaptive-foundation`  
الإصدار: `0.9.3.18` / build `62`

## التشغيل المعتمد

- Compatibility Lab Run: **#31**.
- Run ID: `30377208398`.
- PR head المختبر: `2af1356cd89bdd2d0f0cb7384791d8e8dfdf6449`.
- التغيير دُمج بعد نجاح التأهيل في PR #51.
- Merge SHA على الفرع الرسمي: `75853490d0fd9d7a0ed523eb30133288246094ba`.
- Generated Source Snapshot Run #8: `PASS`.

Run #26 وRun #29 مهمان للمقارنة التاريخية، لكن Run #31 هو مرجع النتيجة الحالية بعد إصلاح TV Search النهائي.

## النتيجة الموحدة

| المؤشر | Run التاريخي الأول | Run #29 قبل إصلاح PR #51 | Run #31 الحالي |
|---|---:|---:|---:|
| Profiles | 9 | 9 | 9 |
| Cases | 133 | 133 | 133 |
| Confirmed Product Critical | 8 | 3 | **0** |
| Raw Critical | 10 | 3 | 2 |
| False-positive Critical | 2 | 0 | 2 |
| Warnings | 252 | 262 | 263 |
| Crash مؤكد | 0 | 0 | 0 |
| ANR مؤكد | 0 | 0 | 0 |
| Infrastructure errors النهائية | 0 | 0 | 0 |

الزيادة البسيطة في warnings ليست دليل regression تلقائيًا؛ معظمها heuristics وقياسات Debug Emulator. لا تستخدم مجموع warnings وحده كقرار قبول أو رفض.

## النتيجة لكل جهاز في Run #31

| الجهاز | API | الحالات | Raw status | Product status بعد مراجعة الأدلة | Critical مؤكد | Warnings |
|---|---:|---:|---|---|---:|---:|
| Pixel 4a | 29 | 28/28 | WARN | PASS مع advisories | 0 | 62 |
| Pixel 6 | 31 | 14/14 | WARN | PASS مع advisories | 0 | 28 |
| Pixel 8 Pro | 35 | 14/14 | WARN | PASS مع advisories | 0 | 31 |
| Galaxy S24 Ultra profile | 35 | 14/14 | WARN | PASS مع advisories | 0 | 32 |
| Nexus 9 | 28 | 14/14 | WARN | PASS مع advisories | 0 | 24 |
| Pixel Tablet | 35 | 28/28 | WARN | PASS مع advisories | 0 | 42 |
| Android TV 720p | 36 | 7/7 | WARN | PASS مع advisories | 0 | 15 |
| Android TV 1080p | 36 | 7/7 | FAIL raw | PASS مع false-positive مصحح | 0 | 17 |
| Android TV 4K | 36 | 7/7 | WARN | PASS مع advisories | 0 | 12 |

كل profiles هي Emulators x86_64، وليست شهادة أجهزة OEM أو ARM فعلية.

## الإصلاحات المثبتة

### Phone landscape navigation — `FIXED`

المشاكل القديمة التي كانت تخفي Downloads وSettings في Landscape على Pixel 6 وPixel 8 Pro وGalaxy profile لم تعد Critical في Run #31. تصنيف الهاتف الأفقي بقي Mobile، والتنقل بقي Top Bar بدل التحول الخاطئ إلى Tablet/Rail.

### TV Search focus trap — `FIXED`

في Run #29 بقي حقل Search محبوسًا على Android TV 720p و1080p و4K لأن IME كان يستهلك D-pad. PR #51 أضاف وضع تنقل read-only ووضع تحرير صريح.

Focus traces في Run #31 أثبتت على الدقات الثلاث:

1. يبدأ التركيز على Search field.
2. `DPAD_DOWN` ينقل التركيز إلى أول بطاقة نتيجة.
3. بقية الأوامر تتحرك بين النتائج والـRail.
4. `unique_focus_targets = 7` لكل TV profile.
5. حالة Search في التقرير أصبحت `PASS` على 720p و1080p و4K.

### TV safe areas — `IMPROVED / ADVISORY REMAINS`

لا توجد Product Critical مرتبطة بالـsafe-area في Run #31. بقيت 6 تحذيرات `tv_safe_area` استشارية تحتاج مراجعة مستقلة قبل أي تعديل إضافي.

### fontScale وoverlap/clipping — `NO CONFIRMED CRITICAL`

لم يظهر Critical مؤكد في حالات الهاتف والتابلت ذات fontScale 1.3. تحذيرات clipping/overlap لا تعتبر عيوبًا مثبتة قبل فحص screenshot/XML حالة بحالة.

## False positive الحالي

Android TV 1080p / Movies سجل:

- `page_marker_missing`.
- `empty_hierarchy`.

لقطة الدليل تعرض Android TV Launcher مع نافذة **Google TV Shop**، وليس تطبيق HULK SA. Navigation audit سجل الوصول إلى Movies، ثم تلوث الالتقاط بنافذة خارج التطبيق. لذلك:

- Raw Critical = 2.
- Confirmed Product Critical = 0.
- التصنيف: `FALSE POSITIVE / LAUNCHER CONTAMINATION`.

يجب تحسين classifier مستقبلًا لتمييز النوافذ الخارجية وعدم رفعها كعيب Product.

## Retry والبنية التحتية

Android TV 720p احتاج retry بعد فشل Emulator عابر. المحاولة النهائية أنتجت 7/7 حالات، `critical_count=0` و`infrastructure_error_count=0`.

هذا يُوثق كعدم استقرار بنية اختبار، ولا يُخفى، لكنه لا يثبت عيبًا في التطبيق.

## توزيع التحذيرات في Run #31

| النوع | العدد | التصنيف الحالي |
|---|---:|---|
| `high_emulator_jank` | 133 | Advisory فقط؛ غير صالح كبوابة أداء |
| `text_at_display_edge` | 42 | يحتاج مراجعة screenshot/XML |
| `slow_page_start` | 39 | Debug Emulator؛ لا يعادل Startup benchmark |
| `possible_text_clipping` | 22 | Heuristic؛ يحتاج إثبات بصري |
| `interactive_overlap` | 21 | Heuristic؛ يحتاج إثبات وظيفي/بصري |
| `tv_safe_area` | 6 | Advisory؛ لا Critical مؤكد |

## ما يثبته المختبر

- reconstruction يعمل.
- Debug source مع harness يتجمع ويُثبت داخل Emulator.
- الصفحات Home/Live/Movies/Series/Search/Downloads/Settings قابلة للالتقاط على profiles المختبرة.
- Phone landscape navigation defects القديمة أغلقت.
- TV Search D-pad focus trap أُغلق.
- لا Crash أو ANR مؤكدة ضمن fixture captures.

## ما لا يثبته المختبر

- real login/catalog/backend.
- real playback أو download I/O.
- signed/minified Release runtime.
- physical ARM/OEM/API 23.
- process death/reboot/network/storage pressure.
- HDR/codecs/subtitles/audio focus.
- Accessibility كاملة أو screenshot regression.
- Macrobenchmark أو performance SLA.

## قرار البوابة

**Compatibility Product Critical gate: PASS بعد التصحيح اليدوي للأدلة.**

الخطوة التالية ليست تعديل UI إضافي بلا دليل؛ بل canonical source governance ثم إصلاح classifier/gate حتى يميز launcher contamination تلقائيًا قبل جعل findings بوابة صارمة.
