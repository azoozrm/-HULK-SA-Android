# HULK SA Android — الخطة الرسمية إلى v1.0

تاريخ التحديث: 2026-07-28 UTC  
الحالة الحالية: v0.9.3.18 / build 62  
HEAD الرسمي: `75853490d0fd9d7a0ed523eb30133288246094ba`

لا تبدأ Feature كبيرة قبل إغلاق بوابات v1.0. لا تبدأ من الصفر ولا تعيد تصميم ما تم إنجازه. الترتيب أدناه إلزامي لأن التوقيع والاختبارات والإصدار يجب أن تُبنى فوق مصدر canonical قابل للتتبع.

## ما أُغلق منذ التدقيق الأول

- Instrumentation وCompose UI Test foundation.
- Launcher/Leanback/MainActivity/TvMainActivity/DPAD coverage.
- Compatibility Lab مربوط بالـPRs ويعمل على 9 profiles.
- Generated Source Snapshot متاح لفحص السورس المولد.
- Phone landscape device classification/navigation defects أغلقت.
- TV Search focus trap أُغلق على 720p/1080p/4K.
- Product Critical المصحح في Run #31 أصبح 0.

هذه الإنجازات لا تغلق canonical source أو signing أو production E2E.

## المرحلة 1 — استقرار البناء وحوكمة المصدر

الحالة: `IN PROGRESS / NEXT`

### العمل المطلوب

1. إعادة تكوين HEAD الحالي v0.9.3.18 من السلسلة الرسمية.
2. تثبيت الناتج كمشروع Gradle canonical مباشر داخل Git.
3. إضافة Gradle Wrapper 8.13.
4. إنشاء Workflow حاكم واحد يعمل من checkout مباشرة ويشغل:
   - `clean`.
   - `lintDebug`.
   - Unit Tests.
   - Debug APK/AAB.
   - Release APK/AAB.
   - R8/resource shrinking.
   - ABI verification.
5. بناء reconstruction output وcanonical output في CI ومقارنة:
   - بنية الملفات الحساسة.
   - Manifest/applicationId/version/SDK/dependencies.
   - source hashes حيث يمكن.
   - APK/AAB contents مع فصل الفروق غير الحتمية مثل timestamps/signatures.
6. إبقاء ZIP والـScripts وWorkflows التاريخية مؤقتًا حتى قبول parity.
7. بعد إثبات parity، اجعل canonical checkout هو مسار البناء الافتراضي.

### شروط القبول

- clone/checkout واحد قابل للفتح والبناء دون تشغيل 25+ patch script أولًا.
- Wrapper موجود ويعمل.
- Workflow canonical أخضر.
- لا تغيير متعمد في السلوك أو UI.
- تقرير parity داخل Artifact وداخل المستودع.
- reconstruction history لم تُحذف.

### مخاطر تمنع الدمج

- اختلاف applicationId أو versionCode/versionName.
- اختلاف Manifest أو network/security policy غير مقصود.
- فقدان ABI.
- حذف ملفات reconstruction قبل parity.
- تعديل واسع غير متعلق بالحوكمة.

## المرحلة 2 — lint clean

الحالة: `BLOCKED BY STAGE 1`

### العمل المطلوب

- تشغيل `lintDebug` على canonical HEAD.
- التحقق من خطأ Media3 unstable API التاريخي في `PlayerScreen`.
- إصلاح الخطأ إن بقي بأقل تغيير صحيح، مثل opt-in بالمجال المناسب، دون تعطيل lint أو baseline يخفي الخطأ.
- فرز warnings:
  - fix required.
  - accepted/documented.
  - false positive.
- جعل lint بوابة Required في Workflow الحاكم.

### شروط القبول

- lint لا يحتوي errors.
- لا suppression واسع غير مبرر.
- warnings المقبولة موثقة.

## المرحلة 3 — التوقيع والتثبيت

الحالة: `BLOCKED BY STAGES 1–2`

### العمل المطلوب

1. التحقق من شهادة مرجع الاستقرار محليًا/ببيئة محمية دون نشرها.
2. إعداد GitHub protected environment وSecrets للمفتاح الرسمي.
3. إضافة signingConfig لا يطبع كلمات المرور أو المسارات الحساسة.
4. إخراج signed Release APK وAAB من canonical HEAD.
5. التحقق من:
   - APK signature schemes.
   - certificate fingerprint parity.
   - AAB signing.
   - zipalign.
6. install clean.
7. upgrade من مرجع الاستقرار مع بقاء البيانات والمسار قابلًا للترقية.
8. R8/minified runtime smoke.

### شروط القبول

- signed APK/AAB Artifacts من CI محمي.
- شهادة الترقية مطابقة للمسار المعتمد.
- clean install وupgrade PASS.
- لا Secrets في Logs أو Artifacts أو PR.

### قرارات خطرة تحتاج توقفًا

- تغيير signing key.
- تغيير applicationId.
- كسر upgrade path.

## المرحلة 4 — المعماريات والأجهزة الفعلية

الحالة: `BLOCKED BY SIGNED RELEASE`

### العمل المطلوب

- physical arm64-v8a.
- physical armeabi-v7a عند توفر جهاز مناسب.
- API 23 minimum runtime.
- Samsung phone/tablet profile فعلي.
- Android TV/Google TV/TCL أو OEM فعلي.
- install/launch/login/playback/download على signed Release.

### شروط القبول

- لا UnsatisfiedLinkError أو ABI packaging regression.
- التطبيق يعمل على الحد الأدنى المدعوم.
- نتائج OEM موثقة بالأجهزة والإصدارات دون بيانات حسابات.

## المرحلة 5 — Production E2E والاعتمادية

الحالة: `BLOCKED BY SIGNED RELEASE`

### مسارات E2E

- real login.
- catalog load.
- Home/Live/Movies/Series/Search.
- playback وtrack/subtitle/audio controls.
- favorites/history.
- download queue/pause/resume/retry/integrity.
- logout/login.

### اختبارات الاعتمادية

- process death.
- reboot.
- network interruption/recovery.
- storage pressure/full disk.
- background restrictions.
- scheduled/Wi-Fi download behavior.
- long-run memory/leak/ANR.

### شروط الأمان

- endpoint وcredentials من Secrets فقط.
- لا screenshots أو logs تكشف بيانات حساب.
- لا fixtures تعتبر بديلًا عن production E2E.

## المرحلة 6 — جودة UI والأداء

الحالة: `PARTIAL`

### العمل المطلوب

- إصلاح Compatibility classifier لتمييز Launcher/IME/system windows.
- تفعيل Product Critical gate بعد إصلاح false positives.
- screenshot regression لمجموعة مرجعية.
- Accessibility checks.
- Macrobenchmark startup/scroll/playback وفق SLA معتمد.
- triage تحذيرات Run #31 حالة بحالة.

### لا يُقبل

- اعتبار `high_emulator_jank` الحالي قياس performance.
- إصلاح UI لمجرد Warning heuristic دون Screenshot/XML.
- ادعاء OEM certification من Emulator.

## المرحلة 7 — Release Candidate وv1.0

الحالة: `NOT STARTED`

### شروط RC

- feature freeze.
- canonical CI أخضر.
- lint clean.
- signed Release install/upgrade PASS.
- Product Critical = 0.
- production E2E PASS.
- physical ARM/OEM qualification مقبولة.
- known issues مصنفة ولا توجد P0/P1 مفتوحة.
- privacy/Data Safety/pre-launch review.

### إصدار v1.0

- protected tag.
- signed APK/AAB مطابقان للـtag.
- release notes.
- staged rollout ومراقبة crash/ANR.
- rollback plan.

لا يُنشر Release عام دون قرار صريح من المستخدم.

## المرحلة 8 — الميزات الكبرى بعد v1.0

تبدأ فقط بعد استقرار v1.0. أي اقتراح ميزة قبل ذلك يُسجل في backlog ولا يُنفذ داخل خط الاستقرار.

## التسلسل التنفيذي المختصر

`Canonical source → lint clean → protected signing → install/upgrade → production E2E → physical ARM/OEM → performance/screenshots → RC → v1.0`
