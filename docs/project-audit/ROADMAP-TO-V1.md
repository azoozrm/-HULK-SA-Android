# HULK SA Android — الخطة الرسمية إلى v1.0

تاريخ التحديث: 2026-07-28 UTC  
الحالة الحالية: v0.9.3.18 / build 62

لا تبدأ Feature كبيرة قبل إغلاق بوابات v1.0. لا تبدأ من الصفر ولا تعيد تصميم ما تم إنجازه.

## المنجز الحاكم

### المصدر والبناء — `COMPLETED`

- مشروع Gradle canonical مباشر موجود.
- Gradle Wrapper 8.13 موجود ومتحقق منه.
- PR #53 أغلق drift بين canonical source وCompatibility reconstruction.
- المسار التاريخي ZIP + patches محفوظ للتدقيق والاسترجاع، لكنه ليس Product source الفعال.
- Canonical source manifest SHA-256 بوابة CI.
- Canonical Build Run #89 نجح في clean/debug/unit/lint/release/R8/ABI.

### التكييف — `COMPLETED WITH ADVISORIES`

- Phone Landscape classification/navigation defects مغلقة.
- TV Search focus trap مغلق على 720p/1080p/4K.
- Canonical Compatibility Run #47: 133/133، Product Critical = 0، Infrastructure = 0.

### signing foundation — `COMPLETED`

- signing inputs fail closed عند النقص.
- unsigned fallback واضح ومتحقق منه.
- verification scripts للـAPK/AAB موجودة.
- Signed Release Qualification preflight نجح.

## المرحلة التالية — Signed Release Qualification

الحالة: `NEXT / BLOCKED BY PROTECTED SECRETS EXECUTION`

### العمل المطلوب

1. تشغيل `HULK SA Signed Release Qualification` يدويًا من protected environment.
2. استخدام Secrets الرسمية فقط:
   - keystore base64.
   - alias.
   - store/key passwords.
   - expected certificate SHA-256.
   - production portal configuration الآمنة.
3. بناء signed Release APK وAAB من v0.9.3.18.
4. التحقق من:
   - applicationId.
   - versionCode/versionName.
   - APK signature schemes.
   - APK/AAB certificate fingerprint.
   - ABI packaging.
   - artifact hashes.
5. عدم طباعة أو رفع signing materials.

### شروط القبول

- signed APK/AAB artifacts من CI محمي.
- certificate fingerprint مطابق للمفتاح المعتمد.
- لا unsigned APK يُقدم كتسليم production.
- لا Secrets في Logs أو Artifacts.

### قرارات خطرة تحتاج توقفًا

- تغيير signing key.
- تغيير applicationId.
- تغيير certificate أو كسر upgrade path.

## المرحلة التالية — Install وUpgrade

الحالة: `BLOCKED BY SIGNED RELEASE`

### العمل المطلوب

- clean install للـsigned APK.
- launch/login smoke.
- upgrade من مرجع الاستقرار.
- التحقق من بقاء بيانات التطبيق ومسار الترقية.
- R8/minified runtime smoke.

### شروط القبول

- install وupgrade PASS.
- لا certificate mismatch.
- لا data loss غير مقصود.
- لا launch/R8 regression.

## Production E2E

الحالة: `BLOCKED BY SIGNED RELEASE`

### المسارات

- real login.
- catalog load.
- Home/Live/Movies/Series/Search.
- playback وtracks/subtitles/audio.
- favorites/history.
- Durable Downloads: queue/pause/resume/retry/integrity.
- logout/login.

### اختبارات الاعتمادية

- process death.
- reboot.
- network interruption/recovery.
- storage pressure/full disk.
- background restrictions.
- scheduled/Wi-Fi download behavior.
- long-run memory/leak/ANR.

### الأمان

- endpoint وcredentials من Secrets فقط.
- لا screenshots أو logs تكشف بيانات حساب.
- fixture matrix لا تعتبر بديلًا عن production E2E.

## Physical ARM/OEM qualification

الحالة: `BLOCKED BY SIGNED RELEASE`

- physical arm64-v8a.
- armeabi-v7a عند توفر جهاز مناسب.
- API 23 minimum.
- Samsung phone/tablet فعلي.
- Android TV/Google TV/TCL أو OEM فعلي.
- playback/download على signed Release.

Emulator x86_64 ليس شهادة أجهزة فعلية.

## UI والجودة والأداء

الحالة: `PARTIAL`

- تفعيل Product Critical gate كشرط صارم مع policy موثقة.
- screenshot regression.
- Accessibility checks.
- Macrobenchmark startup/scroll/playback وفق SLA.
- triage تحذيرات Run #47 حالة بحالة.

لا تستخدم `high_emulator_jank` الحالي كقياس أداء ولا تصلح Warning بصريًا بلا Screenshot/XML.

## Release Candidate وv1.0

الحالة: `NOT STARTED`

### شروط RC

- feature freeze.
- canonical CI وlint أخضران.
- signed Release install/upgrade PASS.
- Product Critical = 0.
- production E2E PASS.
- physical ARM/OEM qualification مقبولة.
- لا P0/P1 مفتوحة.
- privacy/Data Safety/pre-launch review.

### إصدار v1.0

- protected tag.
- APK/AAB مطابقان للـtag.
- release notes.
- staged rollout ومراقبة crash/ANR.
- rollback plan.

لا يُنشر Release عام دون قرار صريح.

## التسلسل التنفيذي

`Signed artifacts → certificate verification → install/upgrade → signed R8 + production E2E → physical ARM/OEM → reliability/performance/screenshots → RC → v1.0`
