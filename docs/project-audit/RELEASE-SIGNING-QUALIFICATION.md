# HULK SA Android — Release Signing Qualification

تاريخ التحديث: 2026-07-28 UTC

## النطاق

هذا المسار يجهز ويشغّل qualification آمنة لـv0.9.3.18. لا ينشئ أو يغير أو يستبدل signing key، ولا ينشر GitHub Release أو Play Store release.

هوية التوقيع الإنتاجية يجب أن تبقى نفس الهوية المستخدمة في التطبيق المستقر القابل للتثبيت. لا يجوز إنشاء مفتاح جديد لمجرد جعل CI أخضر، لأن Android سيرفض upgrade من شهادة مختلفة.

## الحالة الحالية

| المجال | الحالة | الدليل المطلوب |
|---|---|---|
| Canonical unsigned Release | مكتمل | Canonical Build Run #89 |
| Secret-gated Gradle signing | مكتمل كتنفيذ | signing preflight |
| رفض signing inputs الناقصة | مكتمل | fail-closed preflight |
| APK signature/certificate tooling | مكتمل كأداة | signed workflow run |
| AAB signature/certificate tooling | مكتمل كأداة | signed workflow run |
| Production signer identity | غير متحقق | approved keystore + expected SHA-256 |
| Signed APK/AAB لـv0.9.3.18 | غير متحقق | protected signed job |
| Clean signed installation | غير منفذ | signed APK + emulator/device evidence |
| Upgrade من stable APK | غير منفذ | real stable baseline APK بنفس الشهادة |

## GitHub environment وSecrets

Signed job يعمل داخل environment باسم `production-signing` ويقرأ فقط:

- `HULK_RELEASE_KEYSTORE_BASE64`.
- `HULK_RELEASE_KEY_ALIAS`.
- `HULK_RELEASE_STORE_PASSWORD`.
- `HULK_RELEASE_KEY_PASSWORD`.
- `HULK_RELEASE_CERT_SHA256`.
- `HULK_PORTAL_URL` اختياري لمسار production المعتمد.

لا يجوز وضع أي قيمة سرية أو keystore أو password أو private key في Git أو PR أو Logs أو source artifacts.

## Fail-closed behavior

- بلا signing inputs يبني CI Release غير موقع بشكل صريح.
- عند توفير أي signing property تصبح جميع signing properties إلزامية.
- keystore المفقود أو الفارغ يوقف Gradle configuration.
- signed job يفشل إذا كان أي Secret مطلوب غير موجود.
- APK/AAB لا يُرفعان إلا بعد نجاح package/version/signature/certificate/ABI/checksum checks.

## هوية الإصدار المتوقعة

- Application ID: `sa.hulksa.player`.
- Version code: `62`.
- Version name: `0.9.3.18`.

## Qualification الحالية

PR #54 يضيف push trigger محدودًا بالـworkflow وmarker لتشغيل qualification مرة واحدة بعد الدمج على الفرع الرسمي، داخل `production-signing`، بدون نشر عام. بعد اكتمال النتيجة يجب إزالة push trigger والmarker والإبقاء على `workflow_dispatch` كالمسار الدائم.

## Upgrade qualification

`tools/verify-upgrade-compatibility.sh` يفحص static eligibility بين baseline APK وcandidate APK:

- applicationId متطابق.
- signer certificate SHA-256 متطابق.
- candidate versionCode أعلى.
- التوقيعان صالحان تشفيريًا.

هذا لا يساوي install test. القبول النهائي يتطلب:

1. الحصول على APK مرجع الاستقرار الحقيقي.
2. تثبيته وتشغيله.
3. تثبيت signed candidate باستخدام package replacement دون uninstall.
4. التأكد من عدم مسح البيانات.
5. تشغيل النسخة المحدثة.
6. تسجيل version وsigner evidence.

لا يوجد baseline APK صالح داخل المستودع حاليًا؛ لذلك لا يُدعى نجاح upgrade حتى توفير المرجع الحقيقي من مصدر موثوق.

## اختصارات ممنوعة

- لا تولد production key جديدًا لجعل CI أخضر.
- لا تستخدم Debug key لتسليم production.
- لا تقدم unsigned/debug/test-key artifact على أنه Production.
- لا تعتبر تطابق package name وحده دليل upgrade.
- لا تعرض signing material في troubleshooting output.
