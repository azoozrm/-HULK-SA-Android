# HULK SA Reseller Access

بوابة PHP/MySQL صغيرة تعمل على استضافة `hulksa.com` الحالية، وتوفر:

- تسجيل دخول الموزع.
- إضافة أو تعديل مضيف IPTV.
- عرض كود الدخول وتغييره.
- حل الكود من Android عبر `POST /api/reseller/resolve`.

## متطلبات الاستضافة

- PHP 8.1 أو أحدث مع `PDO MySQL`.
- MySQL 8 أو MariaDB 10.5 أو أحدث.
- HTTPS فعال على `hulksa.com`.
- دعم `.htaccess`، وهو متوفر في LiteSpeed المستخدم حاليًا.

## الرفع إلى hulksa.com

1. أنشئ قاعدة بيانات ومستخدمًا مخصصًا لها من لوحة الاستضافة.
2. استورد [schema.sql](schema.sql) في قاعدة البيانات.
3. ارفع **محتويات** مجلد `public/` إلى `public_html/` مع إظهار الملفات المخفية.
4. انسخ:
   `public_html/.hulk-reseller-app/config.example.php`
   إلى:
   `public_html/.hulk-reseller-app/config.php`
5. ضع DSN واسم مستخدم قاعدة البيانات وكلمة المرور في `config.php`، ثم اجعل
   صلاحياته `0600`. لا تحفظ هذا الملف في GitHub.
6. من Terminal في لوحة الاستضافة، أنشئ موزعًا أوليًا دون وضع كلمة المرور في سجل
   الأوامر:

```bash
printf '%s' "$RESELLER_INITIAL_PASSWORD" | \
  php ~/public_html/.hulk-reseller-app/create-reseller.php "اسم الموزع"
```

بعد الرفع تصبح الروابط:

- البوابة: `https://hulksa.com/reseller/`
- API: `https://hulksa.com/api/reseller/resolve`

مجلد `.hulk-reseller-app` محمي من الوصول عبر الويب بواسطة `.htaccess`. يحتوي
GitHub على السورس والمخطط فقط، ولا يحتوي على كلمات مرور أو بيانات موزعين.

## صيغة API

```http
POST /api/reseller/resolve
Content-Type: application/json

{"code":"HULK-ABCD-EFGH-JKMN-PQRS"}
```

النجاح:

```json
{"host":"http://reseller-server.com:8080"}
```

الأخطاء الأساسية:

- `INVALID_CODE` — 404
- `RESELLER_INACTIVE` — 403
- `INVALID_HOST` — 422
- `SERVICE_UNAVAILABLE` — 503

