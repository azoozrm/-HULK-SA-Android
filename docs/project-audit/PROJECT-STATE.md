# HULK SA Android — حالة المشروع المدققة

تاريخ التدقيق: 2026-07-27 UTC
المستودع: <https://github.com/azoozrm/-HULK-SA-Android>
الفرع الرسمي الذي دُقّق: `phase-3-v0.9.3.0-adaptive-foundation`
رأس التطبيق/المختبر الذي بُني واختُبر: `8db147faea8fae0290bf75d53b4194de2035880f`
مرجع المستخدم: `v0.9.3.17-beta`

## قاعدة قراءة الحالة

| الحالة | المعنى في هذه الحزمة |
|---|---|
| `COMPLETED` | موجود في السورس النهائي المولّد، موصول بمسار التطبيق، وله دليل بناء أو اختبار مناسب |
| `PARTIAL` | موجود ويُستخدم، لكن له عيب مؤكد أو لا يغطي السيناريو الإنتاجي كاملًا |
| `NOT IMPLEMENTED` | لا يوجد تنفيذ فعلي في رأس الفرع المدقق |
| `BROKEN` | موجود لكن فشل في دليل تشغيل أو بوابة لازمة |
| `NOT VERIFIED` | قد يكون موجودًا أو مدعى به تاريخيًا، لكن لم يُشغّل أو لا توجد أدلة كافية |

## الملخص التنفيذي

المشروع ليس جاهزًا لإصدار v1.0. التجميع من السورس المعاد تكوينه نجح فعليًا لكل من Debug وRelease، ونجحت 14 وحدة اختبار، ونجح فحص ABI للـ APK والـ AAB. لكن Release APK وAAB غير موقعين، و`lintDebug` يفشل بخطأ واحد، والسورس الحقيقي لا يوجد كمشروع Gradle مباشر في جذر المستودع بل يُعاد توليده من ZIP قديم وسلسلة 24 أداة تحويل.

مختبر التوافق شُغّل على GitHub Actions على 9 ملفات أجهزة/دقات، وأنتج 133 حالة التقاط و692 ملف دليل. بعد المراجعة اليدوية للأدلة:

- 132 حالة هي التقاط فعلي للتطبيق؛ حالة Android TV 720p/Series التقطت Android TV Launcher وليست التطبيق.
- 8 عيوب تطبيق حرجة مؤكدة: 5 حالات تنقل غير قابل للوصول في Landscape على هواتف، و3 حالات Focus trap في Search على Android TV.
- حالتا الفشل الأخريان اللتان عدّهما التقرير حرجتين في TV 720p/Series هما `INVALID / NOT TESTED` وليستا عيب تطبيق.
- لا توجد Crash أو ANR مؤكدة في تشغيل المختبر.
- تحذيرات الأداء البالغ عددها 133 ليست Macrobenchmark؛ اعتمدت على عدد قليل جدًا من الإطارات في Debug Emulator ولا تصلح كبوابة أداء.

الخطوة التالية الوحيدة الموصى بها: إنشاء PR لحوكمة السورس يثبّت الناتج الدقيق لسلسلة التكوين كمشروع Gradle عادي في المستودع، ويضيف Gradle Wrapper 8.13 وWorkflow واحدًا حاكمًا يبني `lint + unit + debug + release` من هذا السورس مباشرة، من دون تغيير سلوك التطبيق.

## هوية Git الفعلية

| البند | النتيجة | الدليل |
|---|---|---|
| الفرع الرسمي الحالي | `phase-3-v0.9.3.0-adaptive-foundation` | طلب المستخدم ورأس تشغيل المختبر |
| الفرع الافتراضي في GitHub | `main` | GitHub repository metadata |
| رأس الفرع الرسمي قبل Commit تقارير التدقيق | `8db147faea8fae0290bf75d53b4194de2035880f` | GitHub Actions run `30287050875` |
| رأس `main` عند التدقيق | `6e0593881c1abd7577c2272fe930d717ee807e56` | GitHub compare metadata |
| علاقة الرسمي بـ `main` | متفرع: الرسمي أمامه 133 Commit وخلفه 2 | GitHub compare؛ merge-base `f6bb35e13c5df442f73d44de5ca38af0a2330c56` |
| العلاقة بفرع Phase 0 | أمامه 123 وخلفه 0 | GitHub compare |
| العلاقة بفرع Phase 2 | أمامه 116 وخلفه 0 | GitHub compare |
| Git tags | لا توجد Tags على remote عند التدقيق | `git ls-remote --tags origin` أعاد نتيجة فارغة |
| عدد الفروع البعيدة | 25 | `git ls-remote --heads origin` |

الفروع القديمة ليست مرجع بناء متساويًا مع الفرع الرسمي. توجد عائلات متفرعة بأسماء مثل `download-system-*` و`v091*` و`phase1/*` و`phase-3-...-adaptive-stable`. لا ينبغي دمج أي منها آليًا؛ يجب أرشفتها أو حذفها بعد مقارنة واعتماد مستقل.

### آخر Commits المهمة في خط الفرع الرسمي

| Commit | الغرض الذي تحقق منه التدقيق |
|---|---|
| `8db147faea8fae0290bf75d53b4194de2035880f` | نسخة GitHub من Commit بناء Compatibility Lab الذي شُغّل |
| `707a4087f1b0e23d8b9ce7da34ef8fe206429a5a` | Workflow وتجميع v0.9.3.17 Stability Polish |
| `e86954e06439bef327066274cbb2207837f3941d` | تفاصيل/Favorites/Downloads v0.9.3.17 |
| `72751e1fedeb06a0f4be915baf67102aafd9c2f6` | Player panels v0.9.3.17 |
| `95ffcd71a73b4b966fba0dc8a4ea03c41fc11e24` | Navigation/categories v0.9.3.17 |
| `a45094e6f290ce18f66226ba781404abdefa2989` | إصلاح تحقق episode padding في v0.9.3.16 |
| `62b00d61821fa430d64197c59f2e074befd82b18` | نقطة بدء إصلاحات v0.9.3.16 |
| `4b62b6f20aabe8dca7a6da3dfde58b0f94f39c45` | إدخال Adaptive Foundation الأولي؛ تلاه تحويل المصدر إلى templates/tools |

هذه الرسائل لا تكفي وحدها لإثبات الميزات؛ جدول حالة الميزات أدناه مبني على السورس النهائي المولّد واستخدامه.

## حقيقة الإصدار

لا يوجد Tag باسم `v0.9.3.17-beta` ولا GitHub Tag لأي إصدار.

| الموضع | القيمة الفعلية |
|---|---|
| `defaultConfig.versionCode` | `61` |
| `defaultConfig.versionName` | `0.9.3.17` |
| Debug suffix | `-beta` |
| Debug package | `sa.hulksa.player.dev` |
| Debug versionName المقروء من APK | `0.9.3.17-beta` |
| Release package | `sa.hulksa.player` |
| Release versionName المقروء من APK | `0.9.3.17` |

لذلك `v0.9.3.17-beta` هو اسم نسخة Debug/مرجع اختبار، وليس Tag ولا اسم Release production.

## السورس الذي يُبنى فعلًا

جذر المستودع لا يحتوي `settings.gradle*` أو `app/` أو `gradlew`. البناء الرسمي يفعل الآتي:

1. يفك `HULK-SA-v0.9.1.20-PHASE1-FINAL-SOURCE(1).zip`.
2. يشغّل `tools/repair-v09120.py`.
3. يشغّل أدوات Phase 2 وPhase 3 بالتتابع من `prepare-v0920-architecture.py` حتى `prepare-v0945-xiaomi-search-safeareas.py`.
4. يشغّل أربعة محولات في `release/v0.9.3.16/`.
5. يشغّل ثلاثة محولات في `release/v0.9.3.17/`.
6. يبني مجلدًا مؤقتًا اسمه `project/`.

Workflow المختبر يستخدم السلسلة الإنتاجية نفسها ثم يضيف `app/src/debug` فقط بواسطة `qa/compatibility/prepare-harness.py`. لا يضيف كود QA إلى `app/src/main`.

حالة المصدر: `BROKEN GOVERNANCE / BUILDABLE`. يمكن بناؤه، لكنه ليس مصدرًا مباشرًا سهل المراجعة أو bisect أو IDE checkout. أي اختلاف في ترتيب أدوات التحويل يغيّر التطبيق النهائي.

## حالة البناء التي شُغّلت

بيئة التدقيق: Temurin JDK 17.0.20، Gradle 8.13، Android SDK 36.

| العملية | النتيجة الفعلية |
|---|---|
| إعادة تكوين السورس | `PASS` |
| `clean` + `compileDebugKotlin` | `PASS` |
| `testDebugUnitTest` | `PASS` — 14/14 |
| `assembleDebug` | `PASS` |
| `bundleDebug` | `PASS` |
| `lintDebug` | `FAIL` — 1 error، 32 warnings، 1 hint |
| `assembleRelease` | `PASS` |
| `bundleRelease` | `PASS` |
| R8/resource shrinking | `PASS`؛ ملف mapping أُنتج |
| Release APK signature | `FAIL` — unsigned |
| Release AAB signature | `FAIL` — unsigned |
| Debug APK install/run | `PASS` داخل Compatibility Lab على 9 emulator profiles |
| Signed Release install | `NOT TESTED` — لا توجد حزمة موقعة |

تفاصيل الأوامر والـ SHA256 في [BUILD-AND-RELEASE-AUDIT.md](BUILD-AND-RELEASE-AUDIT.md).

## الأعمال المنجزة فعليًا

| العمل | الحالة | دليل الاستخدام الحقيقي |
|---|---|---|
| `HomeContentSnapshot` | `PARTIAL` | يُنتج في `NavigationMemoryStore.homeContent()` وتستهلكه Home؛ cache لا يدخل `favorites` في شرط invalidation |
| Smart Home optimization | `PARTIAL` | compact catalog، history/favorites weights، continue watching، featured/popular؛ لا توجد اختبارات سلوكية وعيب cache مؤكد |
| Recommendation improvements | `PARTIAL` | `becauseYouWatched` و`suggested` و`personalizedLive` تظهر في صفوف Home؛ قد تتقادم بعد تغيير Favorite |
| Downloads redesign | `PARTIAL` | queue/pause/resume/priority/Wi-Fi/schedule/progress/integrity/UI موجودة؛ التنفيذ process-bound بلا Worker/Foreground Service |
| Focus improvements | `PARTIAL` | `FocusRequester` وذاكرة موضع وD-pad handling واسعة؛ Search محبوس على TV في 720p/1080p/4K |
| Performance improvements | `PARTIAL / NOT VERIFIED` | Lazy layouts و`remember` وcompact catalog موجودة؛ لا يوجد Macrobenchmark/Baseline Profile قياسي |
| Adaptive phone/tablet/TV foundation | `PARTIAL` | تصنيف width/device/input حقيقي ومختبر بـ6 unit tests؛ rail يخرج عناصر من الشاشة في هواتف Landscape |
| Credential storage | `COMPLETED` للبنية | Android Keystore AES/GCM، backup معطل؛ لم يُختبر migration/فساد المفتاح |
| Playback lifecycle basic cleanup | `COMPLETED` للكود | ExoPlayer داخل `DisposableEffect` ويُستدعى `release()` |
| ABI qualification | `COMPLETED` للحزم المبنية | `arm64-v8a` و`armeabi-v7a` و`x86_64` بالضبط؛ `x86` غير موجود |

## ما لم يكتمل أو لم يُتحقق منه

- توقيع Release، تثبيت Release موقّع، Play App Signing، ورفع Store: `NOT IMPLEMENTED / NOT TESTED`.
- تسجيل دخول حقيقي في المختبر الحالي، backend/catalog production، تشغيل فيديو حقيقي، تنزيل ملف حقيقي: `NOT TESTED`.
- أجهزة فعلية ARM أو OEM فعلية: `NOT TESTED`; الأسماء Galaxy/Pixel هي هندسة Emulator لا عتاد OEM.
- API 23 الفعلي رغم أن `minSdk=23`: `NOT TESTED`; أدنى مختبر API 28.
- Process death، background download، night schedule بعد قتل العملية: `NOT TESTED` ويكشف السورس غياب durable worker.
- Rotation مع عملية/Activity recreation كاملة: `PARTIAL`; الـ Manifest يمتص تغييرات كثيرة عبر `configChanges`.
- Screenshot regression بمرجع ذهبي: `NOT IMPLEMENTED`.
- Macrobenchmark، startup profile generation، long-run playback، LeakCanary، network shaping: `NOT IMPLEMENTED`.
- Store listing، privacy/data safety، policy review: `NOT VERIFIED`.

## تقييم جاهزية v1.0

| المحور | الدرجة /100 | سبب الدرجة |
|---|---:|---|
| Build readiness | 68 | Debug/Release وR8 نجحت، لكن lint يفشل والسورس غير مباشر ولا يوجد Wrapper |
| Installation readiness | 55 | Debug ثبت على 9 محاكيات؛ Release غير موقع ولم يُثبت |
| Phone readiness | 57 | Portrait مغطى، لكن 5 حالات تنقل Landscape مؤكدة عبر ثلاثة أحجام |
| Tablet readiness | 70 | ملفا Pixel Tablet/Nexus 9 بلا عيب حرج مؤكد؛ ما زالت الأدلة Emulator وتحذيرات بصرية غير مفروزة |
| Android TV readiness | 43 | Search focus trap مؤكد على 720p/1080p/4K وحالة Series 720p غير صالحة |
| UI readiness | 55 | 132 لقطة صالحة، لكن عيوب تنقل/Focus و252 warning آلية تحتاج فرزًا |
| Performance readiness | 38 | PSS المرصود ضمن 142 MiB تقريبًا، لكن لا يوجد Macrobenchmark وتحذير jank غير موثوق |
| Release signing readiness | 5 | لا signingConfig ولا key material ولا CI signing path |
| Store readiness | 20 | target/compile 36 وAAB يتكون، لكن unsigned، بلا Tag/Release، lint fail ومراجعة policy غير منفذة |
| **Overall v1.0 readiness** | **44** | لا يمكن إصدار حزمة Production قابلة للتثبيت بثقة، مع عيوب UI حرجة وفجوات اختبار إنتاجية |

هذه الدرجات ليست متوسطًا حسابيًا بسيطًا؛ توقيع Release وحوكمة السورس وعيوب التنقل/Focus بوابات مانعة لا تُعوض بدرجات المجالات الأخرى.

## أهم الموانع

1. `P0`: لا توجد حزمة Release موقعة أو مسار signing آمن.
2. `P1`: السورس النهائي مولّد ولا يوجد كـ canonical Gradle checkout، والفرع الافتراضي ليس الفرع الرسمي.
3. `P1`: Downloads وSettings غير قابلين للوصول في بعض هواتف Landscape.
4. `P1`: Search يسبب Focus trap على كل دقات Android TV المختبرة.
5. `P1`: `lintDebug` يفشل.
6. `P1`: لا توجد اختبارات authenticated production للـ catalog/playback/downloads في رأس الفرع.
7. `P1`: التنزيلات غير durable عند قتل العملية.

القائمة الكاملة مع الأدلة والتحقق المقترح في [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

## GitHub Actions والـ Artifacts

- تشغيل المختبر المكتمل: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875>
- تقرير HTML/Markdown/JSON والأدلة: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661831141>
- APK المختبر: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661311514>
- صفحة Workflow البناء الحالي: <https://github.com/azoozrm/-HULK-SA-Android/actions/workflows/build-v0947-stability-polish.yml>

Artifact التقرير صالح بحسب GitHub حتى 2026-08-26، وArtifacts الأجهزة/APK حتى 2026-08-10. يجب نسخ الدليل الضروري إلى Release أو تخزين طويل الأجل قبل انتهاء الصلاحية.

## الملفات المرجعية التالية

1. [HANDOFF-FOR-NEXT-ENGINEER.md](HANDOFF-FOR-NEXT-ENGINEER.md)
2. [KNOWN-ISSUES.md](KNOWN-ISSUES.md)
3. [BUILD-AND-RELEASE-AUDIT.md](BUILD-AND-RELEASE-AUDIT.md)
4. [COMPATIBILITY-RESULTS.md](COMPATIBILITY-RESULTS.md)
5. [ROADMAP-TO-V1.md](ROADMAP-TO-V1.md)
