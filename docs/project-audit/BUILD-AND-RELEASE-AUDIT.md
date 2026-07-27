# HULK SA Android — تدقيق البناء والإصدار

تاريخ الأدلة: 2026-07-27 UTC
الرأس المدقق: `8db147faea8fae0290bf75d53b4194de2035880f`

## الخلاصة

البناء قابل للتكرار فقط إذا نُفذت سلسلة إعادة التكوين بالترتيب الصحيح. Debug وRelease تم تجميعهما فعليًا، وR8 نجح، لكن `lintDebug` يفشل وRelease غير موقع. Workflow المسمى Stability Polish يبني Debug APK/AAB فقط، رغم أن اسمه يوحي بتسليم إصدار، ولا يختبر `assembleRelease` أو signing أو Store bundle موقّع.

## مصدر البناء وحوكمته

لا يوجد مشروع Android مباشر في جذر Git:

- لا `settings.gradle.kts`.
- لا `app/`.
- لا `gradlew`.
- المصدر الأساسي ملف ZIP واحد: `HULK-SA-v0.9.1.20-PHASE1-FINAL-SOURCE(1).zip`.
- SHA256 للـ ZIP وقت التدقيق: `a7644a48316b1060a5a9ab007b3eceedf7d2295699d194a015bcb6328d001a0d`.

السورس v0.9.3.17 يتكون من ZIP ثم 24 script transformations. نقطة التنفيذ الأكثر اكتمالًا والقابلة لإعادة الاستخدام هي:

```bash
bash qa/compatibility/prepare-project.sh /tmp/hulk-project
```

هذه النقطة تضيف أيضًا debug-only QA harness في `app/src/debug`; لا يتسرب إلى `app/src/main` ولا Release variant. Workflow الإنتاج `build-v0947-stability-polish.yml` يكرر يدويًا السلسلة نفسها من دون harness.

المخاطر:

- السورس الذي يراه المراجع ليس السورس الذي يجمعه Gradle.
- `git blame` و`git bisect` لا يعملان بصورة طبيعية على ملفات التطبيق النهائية.
- تغيير Script مبكر قد يغير ملفات بعيدة عنه.
- Workflow v0.9.3.17 لا يراقب ZIP أو جميع `tools/**` في `paths`; قد يتغير مدخل البناء من دون تشغيل Workflow هذا.
- توجد 22 Workflows، عدد منها قديم أو اسمه لا يطابق الإصدار الذي يبنيه.
- `main` ليس الفرع الرسمي ومتفرع عنه؛ تشغيل build الافتراضي على `main` لا يثبت حالة الفرع الرسمي.

## Toolchain الفعلي

| المكون | القيمة |
|---|---|
| Gradle | 8.13 من setup action/التثبيت المحلي؛ لا Wrapper |
| Android Gradle Plugin | 8.13.2 |
| Kotlin / Compose compiler plugin | 2.2.21 |
| Java source/target/JVM | 17 |
| JDK المستخدم في التدقيق | Temurin 17.0.20 |
| compileSdk | 36 |
| targetSdk | 36 |
| minSdk | 23 |
| namespace/applicationId | `sa.hulksa.player` |
| versionCode | 61 |
| base versionName | `0.9.3.17` |

## الوحدات والـ Variants

- Module واحدة: `:app`.
- Debug:
  - `applicationIdSuffix = ".dev"`.
  - `versionNameSuffix = "-beta"`.
  - موقّع تلقائيًا بشهادة Android Debug.
  - QA harness موجود فقط عند استخدام `qa/compatibility/prepare-project.sh`.
- Release:
  - `isMinifyEnabled = true`.
  - `isShrinkResources = true`.
  - يستخدم `proguard-android-optimize.txt` و`app/proguard-rules.pro`.
  - لا توجد `signingConfig`.

لا توجد product flavors أو build variants أخرى.

## Dependencies الرئيسية

| المجال | Dependency |
|---|---|
| Compose | BOM `2026.06.00`، Foundation، Material3، UI |
| Activity | `androidx.activity:activity-compose:1.13.0` |
| Lifecycle | `lifecycle-runtime-ktx` و`lifecycle-viewmodel-compose` `2.10.0` |
| Coroutines | `1.10.2` |
| HTTP | OkHttp `4.12.0` |
| Images | Coil `3.3.0` + OkHttp/SVG |
| Playback | Media3 ExoPlayer/HLS/UI `1.10.1` |
| Unit tests | JUnit 4.13.2 |

Lint أبلغ أن OkHttp 5.4.0 وCoil 3.5.0 أحدث، لكن الترقية ليست خطوة تلقائية قبل v1.0؛ يجب أن تمر باختبارات network/playback/image regression.

## الأوامر التي نُفذت

تم تمرير قيمة placeholder غير حساسة إلى خاصية portal لأغراض التجميع. لم تُستخدم بيانات دخول.

```bash
gradle --no-daemon --console=plain \
  clean :app:compileDebugKotlin testDebugUnitTest \
  :app:assembleDebug :app:bundleDebug \
  -PHULK_PORTAL_URL=https://example.invalid \
  --stacktrace
```

النتيجة: `BUILD SUCCESSFUL in 5m 38s`، 54 task قابلة للعمل، و14/14 unit tests ناجحة.

```bash
gradle --no-daemon --console=plain \
  :app:lintDebug :app:assembleRelease :app:bundleRelease \
  -PHULK_PORTAL_URL=https://example.invalid \
  --stacktrace
```

النتيجة: `BUILD FAILED in 4m 13s` بسبب `lintDebug`; مراحل Release وR8 كانت قد تقدمت، لكن الأمر ككل فشل.

ثم شُغّل Release منفردًا للتفريق بين فشل lint وفشل التجميع:

```bash
gradle --no-daemon --console=plain \
  :app:assembleRelease :app:bundleRelease \
  -PHULK_PORTAL_URL=https://example.invalid \
  --stacktrace
```

النتيجة: `BUILD SUCCESSFUL in 23s` باستخدام cache، و57 task.

## مخرجات البناء

| الحزمة | الحجم | SHA256 | الحالة |
|---|---:|---|---|
| `app-debug.apk` | 24,440,988 bytes | `faca742c60caa7e151968569b95c53826245ba67cef1630e159cd0eab9728cf0` | Debug-signed |
| `app-debug.aab` | 21,126,427 bytes | `47396d500c1f6c85246869846d3e5023e89f6a304eef293828b359c302b8dce9` | Debug-signed |
| `app-release-unsigned.apk` | 3,244,805 bytes | `9b4e535b8871db9aa8157b95291c81a1fa885507801b47f598d64641c92bf021` | **unsigned** |
| `app-release.aab` | 6,877,804 bytes | `ed4dec629b6d9d43473145137982d2a9645767bb148968822862f6fff50fe472` | **unsigned** |

هذه حزم محلية مؤقتة ليست Artifacts منشورة ولا يجب اعتبارها Release.

## ABI والمعماريات

`ndk.abiFilters` يحدد:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`

تم تشغيل `tools/verify-android-architectures.py` فعليًا على Debug وRelease APK/AAB:

- ZIP integrity: `PASS`.
- مجموعة ABI الدقيقة: `PASS`.
- native library parity: `PASS`.
- ELF machine mapping: `PASS`.
- غياب legacy `x86`: `PASS`.

المكتبة الأصلية الوحيدة هي `libandroidx.graphics.path.so` وموجودة للمعماريات الثلاث. Gradle حذر من عدم قدرته على strip هذه المكتبة وقام بتغليفها كما هي. لم يُختبر التنفيذ على عتاد ARM فعلي.

## R8 وProGuard

- `assembleRelease` نفذ `minifyReleaseWithR8` بنجاح.
- resource shrinking نجح.
- `mapping.txt` أُنتج بحجم 51,884,981 bytes.
- `proguard-rules.pro` صغير؛ models تُحلل بـ`org.json` ولا تستخدم reflection.
- `lintVitalRelease` مرّ ولم يجد issue مانعة.

هذا يثبت قابلية التجميع، لا يثبت تشغيل Release المصغر. يلزم signed Release smoke test لمسارات login/catalog/player/downloads قبل اعتماد قواعد R8.

## خطأ lint المانع

`app/src/main/java/sa/hulksa/player/ui/screens/PlayerScreen.kt:1707`

- ID: `UnsafeOptInUsageError`.
- السبب: استخدام Media3 API (`format.bitrate`) يتطلب `@OptIn(UnstableApi::class)` أو تجنب API غير المستقر.
- النتيجة: `lintDebug` = `FAIL`، 1 error + 32 warnings + 1 hint.

تحذيرات ذات قيمة:

- `UserLibrary.kt:25`: `SharedPreferences.commit()` synchronous.
- `AdaptiveUi.kt:87-88`: استخدام `Configuration.screenWidthDp/screenHeightDp` بدل container/window info.
- `AndroidManifest.xml`: fixed TV orientation discouraged بدءًا من Android 16.
- `network_security_config.xml`: base cleartext configuration غير آمنة.
- `DownloadRepository.kt`: استخدام `File.usableSpace` بدل `StorageManager#getAllocatableBytes`.
- موارد وأيقونات غير مستخدمة/متطابقة.
- تحذيرات compiler: API/constructor deprecated وحالات شرط دائمًا true.

## Signing

حالة signing: `NOT IMPLEMENTED`.

- لا `signingConfigs.release`.
- لا ملفات `.jks/.keystore/.p12/.pem/.key` في المشروع المدقق.
- لا مسار CI يعيد إنشاء keystore من GitHub Secrets.
- Workflow الحالي يرفع Debug APK/AAB فقط.
- Release APK وAAB المحليان غير موقعين.

المطلوب قبل v1.0:

1. تحديد ملكية مفتاح upload وPlay App Signing.
2. تخزين مواد التوقيع في Secrets/بيئة release محمية، لا في Git.
3. إضافة Release job محمي يبني AAB وAPK ويوقعهما ويتحقق بـ`apksigner`/`jarsigner`.
4. تثبيت APK الموقع على phone/TV فعليين.
5. حفظ SHA256 وcertificate digest فقط في تقرير التسليم؛ عدم طباعة أسرار.

## Network/build configuration

`HULK_PORTAL_URL` و`HULK_CONFIG_URL` تتحولان إلى BuildConfig strings. فحص الملفات وجد قيمة endpoint مجمعة صريحة في 22 Workflow موضعًا، لكنه لم يجد username/password أو private signing key. لا تُنسخ قيمة endpoint في هذه الحزمة.

المشكلة ليست أن endpoint وحده secret بالضرورة؛ المشكلة أن configuration production مدمجة في YAML ومتكررة، ويُسمح بـHTTP cleartext، بينما Xtream credentials تدخل query/path. يجب نقل الإعداد إلى environment/secret مُدار واستخدام HTTPS حيث يدعمه الخادم.

## Workflows

`build-v0947-stability-polish.yml`:

- trigger paths تراقب ملف Workflow و`release/v0.9.3.17/**` فقط.
- تعيد تكوين المشروع من ZIP والأدوات.
- تبني Debug APK وDebug AAB.
- تشغّل unit tests.
- تفحص markers نصية في السورس، وليست اختبارات سلوك.
- ترفع source ZIP مولدًا وحزم Debug.
- لا تشغّل lint، Release build، signing، install، أو store validation.

`compatibility-lab.yml`:

- يعيد تكوين المصدر نفسه ويحقن debug harness.
- يبني ويثبت Debug APK على matrix.
- لا يشغّل تلقائيًا بسبب تعديل `docs/project-audit/**`.
- findings لا تفشل Push افتراضيًا؛ `enforce_findings=false` ما لم يُشغل يدويًا بخلاف ذلك.

آخر Run أمكن التحقق منه برقم دقيق هو Compatibility Lab `30287050875`. لم تُرجع واجهة GitHub المتاحة رقم Run دقيقًا لتشغيل Stability Polish التاريخي؛ لذلك تُدرج صفحة Workflow فقط ولا يُخترع رابط Run. دليل البناء الحالي في هذا التقرير مستقل وشُغّل محليًا على السورس المعاد تكوينه.

## قابلية إخراج APK/AAB رسميين

| المخرج | التقييم |
|---|---|
| Debug APK/AAB للاختبار | جاهز تقنيًا |
| Unsigned Release APK/AAB | قابل للتكوين |
| Signed internal APK | غير جاهز |
| Signed production AAB | غير جاهز |
| Store-ready AAB | غير جاهز |

## شروط قبول Build/Release قبل v1.0

- مشروع Gradle canonical في Git مع Wrapper 8.13.
- Workflow واحد يبني نفس السورس الموجود في checkout؛ لا reconstruction chain.
- `lint` بلا errors.
- 14 unit tests الحالية + الاختبارات الجديدة ناجحة.
- `assembleRelease` و`bundleRelease` من clean checkout.
- R8 smoke test موقّع.
- ABI verifier ناجح.
- APK موقع قابل للتثبيت على Phone وTV.
- AAB موقع أو upload-signed ومقبول بـbundletool/Play pre-check.
- Tag مُوقع أو محمي يطابق versionName/versionCode.
- لا endpoint أو secret حساس مطبوع في logs/artifacts.
