# HULK Operations Center

حزمة مستقلة وخفيفة لـ HULK SA مبنية بـ PHP + MySQL. لا تستخدم جداول بوابة الموزعين ولا تحتاج أي بيانات IPTV من التطبيق.

## المتطلبات

- PHP 8.1 أو أحدث مع PDO MySQL وFileinfo، ويفضل ZipArchive.
- MySQL 5.7+/MariaDB 10.4+.
- HTTPS صالح على `hulksa.com`.
- Apache يسمح بملفات `.htaccess`.

## التثبيت المختصر

1. أنشئ قاعدة بيانات ومستخدم MySQL مستقلين باسم مناسب لـ HULK Operations.
2. استورد `schema.sql` مرة واحدة من phpMyAdmin.
3. انسخ `config.example.php` إلى `config.php` وعبئ بيانات قاعدة البيانات فقط. لا ترفع `config.php` إلى GitHub.
4. ارفع محتويات هذا المجلد إلى:
   `public_html/hulk-operations/`
5. لإنشاء أول مسؤول بأبسط طريقة:
   - ضع قيمة قوية ومؤقتة في `app.bootstrap_token` داخل `config.php`.
   - افتح `https://hulksa.com/hulk-operations/admin/setup.php`.
   - أنشئ المسؤول، ثم امسح `bootstrap_token` فورًا.
   - البديل من Terminal: `php public_html/hulk-operations/tools/create_admin.php admin`.
6. افتح لوحة الإدارة وسجل الدخول من:
   `https://hulksa.com/hulk-operations/`
   (أو مباشرة: `https://hulksa.com/hulk-operations/admin/login.php`)
7. اختبر API:
   `https://hulksa.com/hulk-operations/api/app/v1/config/`
8. ارفع APK من قسم **التحديثات**، راجع SHA-256، ثم فعّل الإصدار. الرفع وحده لا ينشره.

## ربط Android

Android يستخدم الطلب الواحد التالي:

`https://hulksa.com/hulk-operations/api/app/v1/config/`

الإعداد الحالي الآمن عند التركيب الأول:

- `latestVersionCode = 64`
- `latestVersionName = 0.9.3.20`
- `minimumSupportedVersionCode = 64`
- `required = false`
- `service.status = OPERATIONAL`
- لا توجد رسالة إجبارية
- جميع Feature Flags المعروفة مفعلة

## التحقق بعد الرفع

1. افتح API وتأكد أن الاستجابة JSON صالحة و`schemaVersion` يساوي 1.
2. جرّب تسجيل الدخول بكلمة مرور خاطئة عدة مرات وتأكد من القفل المؤقت.
3. جرّب إرسال نموذج قديم بعد تحديث الصفحة وتأكد أن CSRF يرفضه.
4. ارفع ملفًا غير APK وتأكد من رفضه.
5. ارفع APK صالحًا وتأكد أن SHA-256 ظهر تلقائيًا.
6. جرّب Version Code مكررًا وتأكد من رفضه.
7. فعّل/عطّل إصدارًا ورسالة وFeature Flag وتأكد من ظهور العملية في سجل العمليات.
8. فعّل `DEGRADED` ثم `MAINTENANCE` واختبر Android، وبعدها أعد الحالة إلى `OPERATIONAL`.

يمكن تشغيل فحوصات الحزمة على الاستضافة أو CI:

```bash
find hosting/hulk-operations -type f -name '*.php' -print0 | xargs -0 -n1 php -l
php hosting/hulk-operations/tests/run.php
python3 -m unittest hosting/hulk-operations/tests/test_backend_contract.py -v
```

## ملاحظات أمنية

- ملفات APK تحفظ في `releases/` باسم مولد آمن، ويمنع تنفيذ PHP داخل المجلد.
- SHA-256 يحسب من الملف المرفوع على السيرفر ولا يقبل من النموذج.
- كل تغييرات الإدارة تستخدم POST + CSRF + Prepared Statements.
- الجلسة تستخدم Secure وHttpOnly وSameSite=Strict.
- لا يسجل Audit Log كلمات مرور أو Tokens أو Credentials.
- الأفضل وضع `config.php` خارج `public_html` وضبط مساره في متغير البيئة `HULK_OPERATIONS_CONFIG` إن كانت الاستضافة تسمح؛ وإلا يحميه `.htaccess` المرفق.
- Operations API للقراءة فقط ولا يستقبل اسم مستخدم IPTV أو كلمة مروره أو كود الموزع أو سجل المشاهدة أو PIN.
