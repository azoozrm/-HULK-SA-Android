# HULK SA Android — قائمة المشاكل المدققة

الحالات:

- `CONFIRMED`: الدليل من تشغيل فعلي أو السورس النهائي واضح.
- `SUSPECTED`: مؤشر قوي يحتاج اختبارًا مخصصًا.
- `INVALID TEST RESULT`: نتيجة مختبر ليست عيب تطبيق.

## P0 — حرجة

### P0-01 — لا يوجد Release موقّع

- الحالة: `CONFIRMED`.
- الوصف: Release APK وAAB يتكونان، لكن لا `signingConfig` وكلاهما unsigned.
- الدليل: `app/build.gradle.kts`; تحقق `apksigner/jarsigner` من المخرجات المحلية.
- المسار: السورس المولّد `app/build.gradle.kts`; `.github/workflows/build-v0947-stability-polish.yml`.
- السيناريو: تثبيت Production أو رفع Store.
- تأثير المستخدم: لا يمكن تسليم/ترقية تطبيق production موثوق.
- الحل المقترح: Play App Signing + upload key في بيئة release محمية + signing job لا يطبع المواد الحساسة.
- التحقق: `apksigner verify --verbose --print-certs`، تثبيت/upgrade على Phone وTV، و`bundletool validate`.

## P1 — عالية

### P1-01 — السورس النهائي غير موجود كمشروع Gradle canonical

- الحالة: `CONFIRMED`.
- الوصف: التطبيق يُولد من ZIP v0.9.1.20 وسلسلة 24 scripts؛ جذر Git لا يحتوي `app/` أو Gradle settings/wrapper.
- الدليل: `qa/compatibility/prepare-project.sh` و`build-v0947-stability-polish.yml`.
- المسار: ZIP، `tools/**`، `release/v0.9.3.16/**`، `release/v0.9.3.17/**`.
- السيناريو: أي build/review/bisect/release.
- تأثير المستخدم: احتمال بناء سورس مختلف عن المراجع، وصعوبة إصلاح regression بثقة.
- الحل: materialize الناتج الدقيق في Git، إضافة Wrapper، وإلغاء reconstruction من Workflow بعد مقارنة byte/source manifest.
- التحقق: clean checkout يبني مباشرة؛ source manifest وunit/APK behavior يطابق baseline؛ لا `project/` generation.

### P1-02 — Downloads/Settings غير قابلين للوصول على هواتف Landscape

- الحالة: `CONFIRMED`.
- الوصف: navigation rail غير scrollable ولا يتسع رأسيًا.
- الدليل: 5 `navigation_failure` في run `30287050875` + مراجعة screenshots/XML + حساب layout.
- المسار: السورس المولّد `ui/screens/MainShellScreen.kt`، `CinematicNavigationRail`.
- الأجهزة: Pixel 6 Downloads/Settings، Pixel 8 Pro Settings، Galaxy S24 Ultra Downloads/Settings، Landscape.
- تأثير المستخدم: صفحة مطلوبة مخفية ولا يمكن الوصول إليها.
- الحل: rail قابل للتمرير أو توزيع compact/overflow يضمن كل destinations ضمن available height مع insets.
- التحقق: semantic click/D-pad لكل destination في landscape small/medium/large؛ screenshot + XML marker للصفحة الصحيحة.

### P1-03 — Search Focus trap على Android TV

- الحالة: `CONFIRMED`.
- الوصف: `BasicTextField`/IME يحتفظ بالـfocus ولا ينتقل D-pad إلى النتائج.
- الدليل: `focus_trap` في 720p و1080p و4K؛ screenshots تعرض keyboard؛ السورس بلا TV focus handoff.
- المسار: `ui/components/HulkComponents.kt:HulkTextField` و`ui/screens/MainShellScreen.kt:UnifiedSearchScreen`.
- السيناريو: Android TV Search مع D-pad.
- التأثير: المستخدم قد لا يصل إلى النتيجة أو يغلق keyboard بطريقة متوقعة.
- الحل: TV search component مستقل، explicit FocusRequester/down route، IME dismissal وBack contract.
- التحقق: D-pad trace ينتقل text field → أول نتيجة → grid/rail ويعود؛ على 720/1080/4K.

### P1-04 — `lintDebug` يفشل

- الحالة: `CONFIRMED`.
- الوصف: Media3 unstable API مستعملة دون opt-in.
- الدليل: 1 error، 32 warnings، 1 hint.
- المسار: `ui/screens/PlayerScreen.kt:1707`.
- السيناريو: quality/release CI.
- التأثير: بوابة التحليل الساكن غير خضراء، وقد يتغير API دون ضمان.
- الحل: opt-in محدد النطاق بعد مراجعة API أو استخدام API stable.
- التحقق: `gradle :app:lintDebug` ينجح، مع بقاء warnings مقبولة موثقة.

### P1-05 — Credentials قد تنتقل عبر HTTP cleartext

- الحالة: `CONFIRMED` كمسار كود؛ الاستغلال مشروط ببوابة HTTP.
- الوصف: cleartext مسموح عالميًا وXtream username/password في query/path.
- الدليل: `AndroidManifest.xml`, `network_security_config.xml`, `XtreamClient.kt`, `ServerDiagnosticsEngine.kt`.
- الجهاز/السيناريو: أي شبكة غير موثوقة مع portal HTTP.
- التأثير: كشف بيانات الاشتراك وروابط البث.
- الحل: HTTPS-only control plane؛ تقييد cleartext بنطاقات streaming ضرورية فقط، وعدم تسجيل URLs، وتوثيق legacy exception.
- التحقق: network security test/packet capture في بيئة مصرح بها يثبت عدم مرور credentials cleartext؛ lint security review.

### P1-06 — التنزيلات ليست durable background work

- الحالة: `CONFIRMED` معماريًا؛ نتيجة process-kill runtime لم تُشغّل.
- الوصف: CoroutineScope داخل repository بلا WorkManager/Foreground Service/alarm.
- الدليل: `DownloadRepository.kt`; عدم وجود Service/Worker/dependency/manifest entry.
- السيناريو: إغلاق التطبيق، process death، night schedule، تنزيل طويل.
- التأثير: توقف تنزيل أو عدم بدء الجدول الليلي وعدم وجود تقدم foreground موثوق.
- الحل: WorkManager للمهام المؤجلة/constraints وForeground Service للتنزيل النشط الطويل، مع notification وresume metadata.
- التحقق: kill process/reboot/network switch/low storage tests؛ يبدأ/يكمل وفق policy ولا يتكرر.

### P1-07 — Compatibility findings لا تفشل CI افتراضيًا

- الحالة: `CONFIRMED`.
- الوصف: `enforce_findings=false` افتراضيًا؛ run أخضر مع product `FAIL`.
- الدليل: `.github/workflows/compatibility-lab.yml`; run `30287050875` كل jobs success مع 10 critical raw.
- السيناريو: branch/release decision.
- التأثير: engineer قد يقرأ check أخضر كاعتماد تطبيق.
- الحل: release dispatch/gate يفرض findings بعد إصلاح false positives، وعرض product status باسم check واضح.
- التحقق: fixture critical مقصودة تجعل release gate يفشل؛ advisory وحده لا يفشل.

### P1-08 — لا E2E إنتاجي في رأس الفرع الحالي

- الحالة: `CONFIRMED` كفجوة تغطية.
- الوصف: الـreal-account E2E والـemulator smoke السابقان حُذفا عند استبدال المختبر، والمختبر الحالي fixture-only.
- الدليل: Commit المختبر حذف `.github/workflows/qa-v09317-real-account-e2e.yml` و`qa/e2e/**`; HEAD لا يحتوي بديل authenticated.
- السيناريو: login مرة واحدة ثم catalog/details/playback/download.
- التأثير: backend أو URL أو parser أو media regression يمكن أن يمر.
- الحل: إعادة Tier E2E اختياري آمن يستخدم GitHub environments/secrets ولا يحفظ credentials في logs/artifacts.
- التحقق: Phone وTV يفتح Home ثم content ثم player/download smoke؛ redaction check؛ لا اختبار login UX نفسه.

## P2 — متوسطة

### P2-01 — Home recommendation cache لا يتغير مع Favorites

- الحالة: `CONFIRMED`.
- الوصف: `homeFavorites` معلن لكنه غير مستخدم في cache key أو assignment.
- الدليل: `NavigationMemoryStore.homeContent()`.
- المسار: `ui/screens/MainShellScreen.kt:167-309`.
- السيناريو: تغيير Favorite ثم البقاء/العودة إلى Home.
- التأثير: صفوف “لأنك شاهدت/مقترح/قنوات مقترحة” قد تبقى قديمة.
- الحل: إدخال `state.favorites` في cache identity/value أو نقل snapshot إلى ViewModel/derived StateFlow.
- التحقق: unit/Compose test يغير Favorite ويثبت تغير snapshot/row دون تغيير catalog/history.

### P2-02 — synchronous SharedPreferences write

- الحالة: `CONFIRMED`.
- الوصف: `replaceFavorites()` يستخدم `commit()`.
- الدليل: lint `ApplySharedPref`.
- المسار: `data/UserLibrary.kt:25`.
- السيناريو: favorite replacement على main thread.
- التأثير: pause/jank محتمل.
- الحل: `apply()` أو IO transaction حسب حاجة الاتساق.
- التحقق: lint clean وStrictMode test بلا disk write على main.

### P2-03 — Adaptive measurement لا يستخدم window container

- الحالة: `CONFIRMED`.
- الوصف: `Configuration.screenWidthDp/screenHeightDp` أساس التصنيف.
- الدليل: lint `ConfigurationScreenWidthHeight`.
- المسار: `ui/adaptive/AdaptiveUi.kt:87-88`.
- السيناريو: split-screen، insets، freeform، foldables، Android 16.
- التأثير: تصنيف rail/bar أو paddings غير ملائم.
- الحل: WindowInfo/WindowSizeClass مبني على container وإدخال insets.
- التحقق: resize/split tests حول 600/840dp، edge-to-edge/cutout.

### P2-04 — TV 720p/Series نتيجة اختبار زائفة

- الحالة: `INVALID TEST RESULT`.
- الوصف: التقرير عدّ marker missing + empty hierarchy كعيبين تطبيق، لكن foreground كان TV Launcher.
- الدليل: XML package `com.google.android.tvlauncher` وscreenshot system shop tutorial.
- المسار: `qa/compatibility/run-lab.py` و`analyze.py`.
- السيناريو: TV system dialog/app departure.
- التأثير: تضخيم critical count؛ Series 720p بقي غير مختبر.
- الحل: verify foreground package/marker قبل التحليل، وتصنيف system UI infrastructure.
- التحقق: analyzer unit test + rerun ناجح Series 720p.

### P2-05 — navigation audit قد يفتح notification shade

- الحالة: `CONFIRMED` في دليل Pixel 6.
- الوصف: swipe أفقي عند y≈4.5% يصطدم status bar بدل UI.
- الدليل: screenshot evidence يظهر notification shade.
- المسار: `qa/compatibility/run-lab.py` navigation interaction.
- السيناريو: phone landscape navigation.
- التأثير: أدلة ملوثة وFalse Positives/Negatives.
- الحل: click by semantics/text/content-desc أو coordinate داخل nav bounds؛ verify foreground/window بعد كل action.
- التحقق: screenshots بلا system shade وmarker الصفحة المطلوبة.

### P2-06 — قياس jank الحالي غير صالح كبوابة

- الحالة: `CONFIRMED`.
- الوصف: 133/133 high jank، غالبًا 4–5 frames في Debug Emulator.
- الدليل: aggregate JSON/gfxinfo parsing.
- المسار: `qa/compatibility/analyze.py`.
- السيناريو: أي صفحة.
- التأثير: كل page status يصبح WARN وتضيع الإشارة الحقيقية.
- الحل: إزالة jank من compatibility pass/fail؛ Macrobenchmark Release منفصل بiterations.
- التحقق: benchmark confidence interval وframe counts كافية.

### P2-07 — بدء التطبيق يقرأ/يفك persistence على main thread

- الحالة: `SUSPECTED` من الكود؛ لا ANR مثبت.
- الوصف: ViewModel constructor ينشئ UserLibrary/DownloadRepository ويقرأ JSON ويفحص ملفات قبل أول frame.
- الدليل: `HulkViewModel.kt` constructor، `DownloadRepository.init/readStored/downloads`.
- السيناريو: history/download metadata كبيرة أو storage بطيء.
- التأثير: startup delay/jank.
- الحل: repository initialization على IO، schema-bound store وobservable flow.
- التحقق: StrictMode + startup Macrobenchmark بmetadata كبيرة.

### P2-08 — لا Android instrumentation/screenshot regression tests

- الحالة: `CONFIRMED`.
- الوصف: `app/src/androidTest` لا يحتوي tests؛ توجد 14 unit tests فقط.
- الدليل: source inventory وGradle dependencies.
- السيناريو: Compose navigation/focus/layout.
- التأثير: أخطاء UI الحالية لم تمنع builds التاريخية.
- الحل: Compose UI tests، screenshot baselines، orchestrator/isolation، accessibility assertions.
- التحقق: tests تفشل على rail/focus defects ثم تنجح بعد الإصلاح.

### P2-09 — version/tag/release governance غير متسقة

- الحالة: `CONFIRMED`.
- الوصف: baseline يسمى beta، release version لا يحتوي beta، ولا Tags؛ `main` ليس الرسمي.
- الدليل: Gradle badging، `git ls-remote --tags`, repository metadata.
- السيناريو: تسليم/rollback/support.
- التأثير: صعوبة معرفة binary ↔ source.
- الحل: branch/default policy، protected release tag، immutable manifest وchecksums.
- التحقق: كل Release artifact يشير إلى tag/commit/versionCode/certificate.

### P2-10 — service endpoint مكرر في 22 Workflow

- الحالة: `CONFIRMED`.
- الوصف: build property endpoint صريح ومكرر؛ لم تُكتشف credentials أو keys.
- الدليل: secret-class scan بالمسارات فقط.
- المسار: `.github/workflows/*.yml`.
- السيناريو: rotation أو تغيير البيئة أو log review.
- التأثير: configuration drift وإفشاء بنية خدمة غير لازمة.
- الحل: GitHub environment variable/secret وWorkflow reusable واحد.
- التحقق: scan لا يجد endpoint literals؛ build config injection تعمل بلا طباعة القيمة.

## P3 — تحسينات

### P3-01 — Workflows كثيرة وقديمة أو مسماة خطأ

- الحالة: `CONFIRMED`.
- الدليل: 22 Workflow؛ أمثلة أسماء ملفات v0941/v0942 لا تطابق `name`/الإصدار.
- التأثير: اختيار pipeline خاطئ وصيانة مضاعفة.
- الحل: Workflow حاكم + reusable components ثم أرشفة القديم.
- التحقق: workflow inventory موثق ومسار release واحد.

### P3-02 — Gradle Wrapper مفقود

- الحالة: `CONFIRMED`.
- التأثير: local/CI reproducibility تعتمد على تثبيت خارجي.
- الحل: إضافة Wrapper 8.13 والتحقق من checksum.
- التحقق: `./gradlew --version` وclean build.

### P3-03 — تحذيرات compiler وشروط دائمًا true

- الحالة: `CONFIRMED`.
- المسارات: `MainActivity.kt`, `MainShellScreen.kt`, `MovieDetailsScreen.kt`, `SeriesScreen.kt`.
- التأثير: ضوضاء واحتمال منطق متبقٍ غير ضروري.
- الحل: مراجعة موضعية بلا تغيير behavior غير مقصود.
- التحقق: compile warnings تقل واختبارات المسارات تمر.

### P3-04 — ملفات/موارد متطابقة وغير مستخدمة

- الحالة: `CONFIRMED`.
- الدليل: `.payload/source/part-00` يطابق `payload/text/part-00.b64`; logo/banner متطابقان وفق lint.
- التأثير: repository ambiguity وحجم/ضوضاء.
- الحل: حذف النسخة غير المرجعية بعد إثبات عدم اعتماد workflow عليها.
- التحقق: جميع Workflows/builds تعمل وduplicate scan نظيف.

### P3-05 — ملفات production ضخمة

- الحالة: `CONFIRMED`.
- المسارات: MainShell 2,727، Player 1,762، DownloadRepository 1,082.
- التأثير: مراجعة واختبار أصعب.
- الحل: بعد تثبيت tests، استخراج components/use cases تدريجيًا بلا redesign.
- التحقق: behavior/screenshot/unit parity.

### P3-06 — README لا يصف المشروع

- الحالة: `CONFIRMED`.
- الدليل: `README.md` يحتوي عنوانًا فقط.
- التأثير: onboarding خاطئ واعتماد على المحادثات.
- الحل: بعد اعتماد هذه الحزمة، رابط واضح إلى `docs/project-audit/HANDOFF-FOR-NEXT-ENGINEER.md`.
- التحقق: clean-room engineer يبني ويشغل الاختبارات من docs فقط.
