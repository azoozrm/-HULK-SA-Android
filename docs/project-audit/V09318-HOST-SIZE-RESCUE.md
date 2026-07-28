# HULK SA Android — v0.9.3.18 Host and APK Size Rescue

تاريخ التشخيص: 2026-07-28 UTC
الفرع الرسمي المفحوص: `phase-3-v0.9.3.0-adaptive-foundation`
الـHEAD المفحوص: `55ee9a136d3557a97daa9b9c2a4821de75108652`

## النتيجة التنفيذية

نسخة v0.9.3.18 الموقعة صحيحة من ناحية package/version/signature/ABI، لكنها مرفوضة كنسخة تشغيل لأن هوست Xtream المجمّع داخلها هو `https://hulksa.com/` بدل هوست HULK التشغيلي.

فرق الحجم بين v0.9.3.17 وv0.9.3.18 مفسر بالأدلة: المرجع السابق Debug غير مصغر، والجديد Release مع R8 وresource shrinking. لم يثبت حذف Media3 أو OkHttp أو Coil أو Compose أو موارد التلفزيون أو أي ABI مطلوب.

## سلسلة الأدلة

### v0.9.3.18

- Workflow Run: `30400862864`.
- Run head: `d3c602d3bcc239bde43af834a6d9559f4ac74964`.
- Artifact: `HULK-SA-SIGNED-RELEASE-QUALIFICATION`.
- Artifact ID: `8704864097`.
- Artifact ZIP SHA-256: `ae285bac47a2c16dcb99a27340df2000295ce91261a4a8af9b7073d6e4ac5e0c`.
- APK SHA-256: `ea658260909c18efb8416050d05f036dc138a0fd1b09e2b2709433e7b9894354`.
- الـAPK المرفق والـAPK داخل Artifact متطابقان بايتًا.

### v0.9.3.17

- Workflow Run: `30236076215`.
- Run head: `707a4087f1b0e23d8b9ce7da34ef8fe206429a5a`.
- Artifact: `HULK-SA-v0.9.3.17-STABILITY-POLISH`.
- Artifact ID: `8641689516`.
- Artifact ZIP SHA-256: `2937a0021424469fae9cfdbd6eb8ed4843b1781d7533327ad801f5e5a2d1e86e`.
- Debug APK SHA-256: `d946e79bdc0d18f72a57f7498159e8ff18f30d7d4363d3bbca9e57bcd5eae7f5`.

## السبب الجذري للهوست

1. `app/build.gradle.kts` كان يقرأ `HULK_PORTAL_URL` بلا default تشغيلي آمن.
2. Signed workflow كان يقرأ `secrets.HULK_PORTAL_URL` مع fallback إلى `https://example.invalid`.
3. Logs أثبتت أن القيمة كانت موجودة ومخفية، لذلك fallback لم يُستخدم.
4. DEX الفعلي أثبت أن `BuildConfig.PORTAL_URL` جرى inlining له إلى:

   `https://hulksa.com/`

5. DEX أثبت أن `BuildConfig.CONFIG_URL` كان فارغًا.
6. `XtreamClient` يبني طلب المصادقة بإضافة `/player_api.php` إلى `PORTAL_URL`.
7. استجابة موقع الويب HTML/Challenge تُحوّل بواسطة `looksLikeChallenge()` إلى `XtreamException.ServiceBlocked`، وهي نفس الرسالة التي ظهرت على Galaxy.

النتيجة: Secret الإنتاج كان موجودًا لكنه يحمل هوست الموقع، وليس هوست HULK التشغيلي.

## cleartext وAndroid 9+

الـAPK الموقّع نفسه يحتوي:

- `android:usesCleartextTraffic="true"`.
- `android:networkSecurityConfig="@xml/network_security_config"`.
- `base-config cleartextTrafficPermitted="true"`.
- system trust anchors.

إذًا HTTP إلى `http://3162356.xyz:8080` مسموح في merged manifest. cleartext ليس سبب فشل v0.9.3.18.

## فصل أنواع الروابط

| النوع | القيمة | القرار |
|---|---|---|
| Xtream API والمحتوى | `http://3162356.xyz:8080` | الهوست التشغيلي الوحيد |
| شراء وتجديد | `https://hulksa.com/` | يبقى رابط ويب فقط |
| حساب العميل | `https://hulksa.com/account/login.php` | يبقى رابط ويب فقط |
| تحميل التطبيقات | `https://hulksa.com/hulk-app/` | يبقى رابط ويب فقط |
| CONFIG_URL | فارغ في Release | يمنع override غير قابل للتحقق |

## مقارنة حجم APK

| المؤشر | v0.9.3.17 Debug | v0.9.3.18 Release | النتيجة |
|---|---:|---:|---|
| حجم APK | 24,424,419 | 3,426,648 | Release أصغر 85.97% |
| DEX count | 11 | 1 | R8 أعاد الدمج بعد التقليص |
| DEX bytes غير مضغوطة | 71,904,516 | 4,587,808 | تقليص 93.62% |
| Class definitions | 34,315 | 4,703 | إزالة/دمج الكود غير المستخدم |
| Method references عبر DEX | 205,094 | 27,775 | R8 optimization |
| res files | 287 | 222 | resource shrinking |
| res bytes غير مضغوطة | 1,141,762 | 1,086,836 | الصور والخطوط الأساسية باقية |
| resources.arsc | 776,996 | 386,040 | تقليص resource table |
| Native libraries | 3 | 3 | متطابقة |
| ABI | arm64-v8a, armeabi-v7a, x86_64 | نفسها | متطابقة |

## دليل بقاء المكتبات

DEX v0.9.3.18 يحتوي أدلة مباشرة على:

- `androidx.media3.exoplayer.ExoPlayer`.
- `androidx.media3.exoplayer.hls.HlsMediaSource.Factory`.
- Media3 UI وsubtitle وtrack selection.
- OkHttp.
- Coil network/disk cache.
- Compose runtime/foundation/UI.
- WorkManager وforeground service.

كما بقيت native library family نفسها لكل ABI. لذلك لا يوجد دليل أن الـAPK split أو stub أو thin أو مبني من module مختلف.

## الإصلاح المحدود

- جعل `http://3162356.xyz:8080` default تشغيليًا صريحًا في Gradle.
- إضافة Release fail-closed task يرفض:
  - القيمة الفارغة.
  - `example.invalid`.
  - `hulksa.com`.
  - أي قيمة غير الهوست المعتمد.
  - أي `CONFIG_URL` غير فارغ.
- إزالة اعتماد runtime host على Secret.
- إضافة verifier يقرأ generated `BuildConfig.java` وDEX داخل APK/AAB.
- رفع تقارير runtime configuration ضمن Artifacts.
- إبقاء روابط الشراء والحساب والدعم على `hulksa.com`.
- عدم تعديل UI أو UX أو منطق الميزات.

## حالة التحقق

| البند | الحالة |
|---|---|
| سبب الهوست في APK القديم | `PROVEN` |
| سبب فرق الحجم | `PROVEN` |
| cleartext في merged APK | `PASS` |
| patch محدود في فرع إصلاح | `IMPLEMENTED` |
| unit tests لأداة verifier | `IMPLEMENTED` |
| Debug/Release CI من patch | `PENDING` |
| signed APK/AAB جديدان | `PENDING` |
| Galaxy runtime login | `NOT VERIFIED` |
| Xiaomi/Android TV runtime | `NOT VERIFIED` |
| merge إلى الفرع الرسمي | `BLOCKED UNTIL RUNTIME` |
