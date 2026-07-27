# HULK SA Android — الخطة الرسمية إلى v1.0

هذه الخطة مبنية على الأدلة في 2026-07-27. لا تبدأ Feature كبيرة قبل إغلاق مراحل v1.0. ترتيب المراحل إلزامي لأن توقيع أو إصلاح واجهة فوق سورس مولّد وغير canonical يعيد إنتاج المخاطر نفسها.

## قرار البداية

الخطوة التالية الوحيدة: PR لحوكمة السورس يثبت ناتج v0.9.3.17 الحالي كمشروع Gradle مباشر، مع Gradle Wrapper وWorkflow حاكم، من دون تغيير Business Logic أو UI.

## المرحلة 1 — استقرار البناء وحوكمة السورس

- الهدف: جعل checkout نفسه هو السورس الذي يبنيه Gradle.
- المشاكل: `P1-01`, `P1-04`, `P2-09`, `P2-10`, `P3-01`, `P3-02`.
- الأنظمة المتأثرة: جذر Gradle، `app/`، `.github/workflows/`، أدوات reconstruction، version metadata.
- الحجم: `كبير`.
- الاعتماديات: الرأس المدقق وmanifest/hash للسورس المولّد؛ لا تعتمد على فرع قديم.

خطوات التنفيذ:

1. توليد v0.9.3.17 مرة واحدة من الرأس المدقق وتسجيل manifest hashes.
2. إضافة المشروع الناتج مباشرة إلى الفرع الرسمي، مع Wrapper 8.13.
3. الاحتفاظ بالـZIP/scripts في `legacy-source-reconstruction/` أو Tag تاريخي مؤقتًا، لا حذفها قبل parity.
4. إنشاء Workflow واحد: clean, compile, unit, lint, debug APK/AAB, unsigned release APK/AAB, ABI checks.
5. جعل workflow يراقب كل سورس Gradle الحقيقي.
6. إصلاح خطأ Media3 lint فقط، بلا refactor أو redesign.
7. نقل endpoint configuration إلى GitHub environment وعدم طباعة القيمة.
8. توثيق versionCode/versionName والفرع الافتراضي/الرسمي.

شروط القبول:

- clean clone يبني بـ`./gradlew`.
- لا reconstruction لإنشاء `app/src/main`.
- 14/14 unit tests تمر.
- `lint` بلا errors.
- Debug وRelease/R8 يتكونان.
- ABI set يطابق الثلاثة.
- source manifest متطابق مع baseline أو كل فرق مفسر ومراجع.

الاختبارات:

- `./gradlew clean lint testDebugUnitTest assembleDebug bundleDebug assembleRelease bundleRelease`.
- architecture verifier لكل APK/AAB.
- source diff parity.
- secret/config scan بالمسارات دون إخراج القيم.

المخاطر:

- إدخال فرق whitespace/generated resource كبير يخفي فرق behavior.
- حذف script تاريخي مبكرًا يمنع مقارنة parity.
- تغيير dependencies أثناء النقل يخلط الحوكمة مع upgrade.

ما يمنع المرحلة التالية:

- أي اختلاف غير مفسر في سورس التطبيق النهائي.
- lint error.
- عدم وجود Wrapper/clean build.
- أكثر من pipeline “رسمي”.

## المرحلة 2 — التثبيت والتوقيع

- الهدف: إنتاج Release APK/AAB موقّعين وقابلين للتتبع والتثبيت.
- المشاكل: `P0-01`, جزء من `P2-09`.
- الأنظمة: Gradle signing، GitHub Environments/Secrets، release workflow، Play App Signing، artifact manifest.
- الحجم: `متوسط`.
- الاعتماديات: اكتمال المرحلة 1 وقرار مالك المفتاح.

خطوات التنفيذ:

1. تحديد upload key/Play App Signing وسياسة rotation.
2. إنشاء release environment محمي بالموافقات.
3. إدخال key/passwords كـSecrets؛ ممنوع Git/log/artifact.
4. إضافة signing job، `apksigner`/`jarsigner` verification، وbundletool validation.
5. تثبيت clean install وupgrade من baseline مع الاحتفاظ بالبيانات.
6. إنشاء immutable artifact manifest: commit/tag/versionCode/versionName/SHA256/cert digest.

شروط القبول:

- signed APK يثبت ويعمل على Phone وTV.
- signed/upload-signed AAB يمر bundle validation.
- upgrade path ينجح.
- لا secret يظهر في logs.
- R8 mapping محفوظ بمكان محمي مرتبط بالإصدار.

الاختبارات:

- signature verify.
- clean install/launch/logout/login.
- upgrade preserving encrypted credentials/history/download metadata.
- minified Release smoke.

المخاطر:

- مفتاح تاريخي غير متاح.
- applicationId/certificate mismatch يمنع upgrade.
- إساءة logging لكلمات المرور.

ما يمنع المرحلة التالية:

- unsigned artifact.
- install/upgrade failure.
- certificate ownership غير موثق.

## المرحلة 3 — تأهيل المعماريات والعتاد

- الهدف: تحويل ABI verifier من فحص archive إلى اعتماد runtime.
- المشاكل: فجوة ARM/OEM وAPI 23.
- الأنظمة: native packaging، Media3/Coil/Compose native libs، device lab.
- الحجم: `متوسط`.
- الاعتماديات: signed internal Release من المرحلة 2.

خطوات التنفيذ:

1. تثبيت signed Release على arm64 phone/tablet/TV فعلي.
2. تثبيت على جهاز armeabi-v7a فعلي إن كان v1 يدعمه رسميًا.
3. تحديد هل `x86_64` production مطلوب أم للاختبار فقط.
4. اختبار API 23 minimum الحقيقي أو رفع minSdk بقرار منتج موثق.
5. تشغيل catalog/artwork/player/download smoke لكل ABI مدعوم.
6. اختبار low-memory/process pressure.

شروط القبول:

- لا `UnsatisfiedLinkError`.
- playback/artwork يعملان.
- ABI policy موثقة ومتوافقة مع Store.
- API minimum مدعوم فعليًا أو تغير رسميًا.

الاختبارات:

- APK/AAB ELF verifier.
- physical runtime smoke.
- low-memory kill/relaunch.
- bundle split install بواسطة bundletool.

المخاطر:

- صعوبة توفر armeabi-v7a/TV عتاد.
- codec/decoder differences لا تظهر على emulator.

ما يمنع المرحلة التالية:

- ABI crash.
- عدم قرار minimum/ABI policy.
- signed build لا يعمل على ARM.

## المرحلة 4 — تكييف الهاتف والتابلت والتلفزيون

- الهدف: إغلاق عيوب layout/focus/navigation المؤكدة قبل توسيع التغطية.
- المشاكل: `P1-02`, `P1-03`, `P2-01`, `P2-03`, وتحذيرات safe/clipping التي تؤكدها المراجعة.
- الأنظمة: `MainShellScreen`, `HulkComponents`, `AdaptiveUi`, Home snapshot، insets/focus/IME.
- الحجم: `كبير`.
- الاعتماديات: canonical source والاختبارات الأساسية.

خطوات التنفيذ:

1. كتابة Compose tests تفشل حاليًا للـrail وTV Search.
2. إصلاح rail ليضمن وصول 8 destinations لكل available height.
3. إنشاء TV Search focus/IME contract.
4. إصلاح Home cache key ليشمل favorites.
5. الانتقال إلى container/window metrics مع insets.
6. فرز screenshots لكل 252 warning؛ تحويل المؤكد فقط إلى issues/baselines.
7. اختبار keyboard/back/rotation/density/font scale/RTL.
8. عدم إعادة تصميم theme/cards؛ تعديلات تكيفية موضعية.

شروط القبول:

- كل destination يصل إليه touch وD-pad وsemantic test.
- TV Search ينتقل من field إلى النتائج ويعود.
- لا out-of-screen/hidden critical.
- Home recommendations تتحدث مع favorite.
- 720/1080/4K وphone/tablet portrait/landscape بلا عيب P1.

الاختبارات:

- Compose UI instrumentation.
- screenshot regression approved.
- Compatibility Lab بعد إصلاح analyzer وبـ`enforce_findings=true`.
- physical TV overscan/D-pad.
- font 1.0/1.3/1.5 وRTL.

المخاطر:

- إصلاح rail للهاتف قد يغير TV layout.
- IME behavior يختلف بين AOSP وOEM.
- screenshot baselines قد تصبح noisy إذا لم تثبت البيانات/fonts.

ما يمنع المرحلة التالية:

- أي critical navigation/focus.
- حالة `NOT TESTED` في page/device mandatory.
- اختلاف تصميم غير معتمد.

## المرحلة 5 — منظومة الاختبارات والجودة

- الهدف: بوابات موثوقة تقلل False Positives/Negatives وتغطي production paths.
- المشاكل: `P1-07`, `P1-08`, `P2-04`, `P2-05`, `P2-06`, `P2-07`, `P2-08`.
- الأنظمة: `qa/compatibility`, Android tests، Macrobenchmark، E2E، downloads worker.
- الحجم: `كبير`.
- الاعتماديات: layout/focus contract ثابت، signed internal Release.

خطوات التنفيذ:

1. فحص foreground package وإصلاح interaction في المختبر.
2. فصل status: product critical / advisory / infrastructure.
3. جعل Release gate enforce critical findings.
4. إضافة Compose UI tests وAndroid Test Orchestrator.
5. إضافة screenshot regression ثابت؛ Paparazzi/Roborazzi للاختبارات السريعة، وdevice screenshots للـfocus/insets.
6. إضافة Macrobenchmark/Baseline Profile على Release-like build.
7. إعادة authenticated E2E tier آمن: login مرة واحدة ثم Home/content/player/download.
8. إضافة network shaping، process death، low storage، reboot، background download.
9. إضافة playback soak، codec/HLS/TS/subtitle/audio tests.

شروط القبول:

- المختبر لا يصنف Launcher كعيب تطبيق.
- critical fixture يفشل gate وadvisory لا يلوث PASS.
- E2E production smoke ناجح بلا secret leakage.
- Macrobenchmark baseline وbudgets معتمدة.
- download survives policy scenarios.
- no known flaky mandatory test.

الاختبارات:

- Python lab tests.
- Compose UI/instrumentation.
- screenshot golden tests.
- Macrobenchmark.
- authenticated E2E.
- chaos/network/storage/process tests.

المخاطر:

- credentials/service availability يسبب flakiness.
- emulator performance غير ممثل.
- golden images حساسة لتغير fonts/rendering.

ما يمنع المرحلة التالية:

- mandatory flaky test.
- false green critical.
- عدم وجود Release E2E/performance evidence.

## المرحلة 6 — إصدار v1.0 مستقر

- الهدف: Candidate موقّع، قابل للرجوع، ومستوفي الجودة/Store.
- المشاكل: كل release blockers المفتوحة.
- الأنظمة: release branch/tag، artifacts، Store listing/policy، monitoring.
- الحجم: `متوسط`.
- الاعتماديات: اكتمال المراحل 1–5.

خطوات التنفيذ:

1. تجميد الميزات.
2. رفع versionCode وversionName وفق سياسة رسمية.
3. تشغيل full release pipeline من clean protected tag.
4. internal/closed testing rollout.
5. مراجعة Data Safety/privacy/network permissions/TV listing/assets.
6. مراقبة crash/ANR/playback/download KPIs.
7. staged rollout مع rollback criteria.

شروط القبول:

- صفر P0/P1 مفتوحة.
- P2 المتبقية لها قبول خطر مكتوب ومالك.
- signed AAB/APK + mapping + checksums + SBOM/dependency report.
- Store pre-launch report بلا blocker.
- Phone/Tablet/TV acceptance.
- rollback artifact/key/process جاهز.

الاختبارات:

- full CI matrix.
- physical acceptance.
- install/upgrade/rollback.
- Store pre-launch.
- 24h soak للعينة المناسبة.

المخاطر:

- backend drift أثناء candidate.
- Store policy/API behavior changes.
- signing/rollout misconfiguration.

ما يمنع الانتقال:

- لا انتقال إلى post-v1 قبل إصدار مستقر ومراقب وإغلاق P0/P1.

## المرحلة 7 — مميزات ما بعد v1.0 فقط

- الهدف: إضافة المميزات الكبرى بعد فصلها عن الاستقرار.
- المشاكل: ليست موانع v1.
- الأنظمة: تحددها Product Roadmap لاحقًا.
- الحجم: `كبير` لكل initiative ويقدر منفصلًا.
- الاعتماديات: v1.0 stable metrics وcanonical architecture.

خطوات التنفيذ:

1. ترتيب backlog بالبيانات لا بالأسماء التاريخية.
2. RFC لكل feature كبيرة.
3. tests/telemetry قبل التنفيذ.
4. incremental modules/refactor فقط عند حاجة feature.

شروط القبول:

- لا regression في v1 gates.
- feature flag/rollback للمخاطر.
- نتائج Phone/Tablet/TV مستقلة.

الاختبارات:

- unit/UI/E2E/performance حسب feature.

المخاطر:

- إعادة فتح ديون المصدر أو layout.
- توسيع scope قبل استقرار القياسات.

ما يمنع التنفيذ:

- أي P0/P1 v1 مفتوحة أو عدم استقرار production.

## مؤشرات الخروج إلى v1.0

| المؤشر | الهدف |
|---|---|
| Build/lint/unit | 100% mandatory green |
| Signed install/upgrade | Phone + Tablet + TV |
| Compatibility critical | 0 |
| Invalid mandatory cases | 0 |
| Crash/ANR في release testing | 0 blocker؛ thresholds موثقة |
| TV D-pad journeys | Home→كل صفحة→content→player→Back |
| Performance | budgets من Macrobenchmark/physical، لا emulator advisory |
| Security | لا credentials cleartext أو logs؛ exceptions موثقة ومقيدة |
| Traceability | Tag → commit → source → artifacts → checksums → certificate |
