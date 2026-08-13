# HULK SA Reseller Access

نظام PHP/MySQL صغير يعمل على استضافة `hulksa.com` الحالية، ويشمل:

- بوابة موزع لإدارة مضيف IPTV وكود الدخول وكلمة مرور البوابة.
- لوحة إدارة لإنشاء الموزعين وربط المضيف وتغيير الكود وكلمة المرور والحالة.
- بحث وتصفية ونسخ ومشاركة أكواد الدخول.
- API لحل الكود من Android عبر `POST /api/reseller/resolve/`.
- واجهة عربية متجاوبة بخط IBM Plex Sans Arabic محلي وشعار HULK الرسمي.

## الروابط

- بوابة الموزع: `https://hulksa.com/reseller/`
- إدارة الموزعين: `https://hulksa.com/hulk-reseller-admin/`
- API: `https://hulksa.com/api/reseller/resolve/`

## متطلبات الاستضافة

- PHP 8.1 أو أحدث مع `PDO MySQL`.
- MySQL 8 أو MariaDB 10.5 أو أحدث.
- HTTPS فعال على `hulksa.com`.
- دعم `.htaccess`.

## الرفع إلى hulksa.com

1. استورد [schema.sql](schema.sql) عند التثبيت الجديد فقط.
2. ارفع مجلدات `reseller/` و`hulk-reseller-admin/` إلى `public_html/`.
3. ارفع `.hulk-reseller-app/bootstrap.php` مع إبقاء ملف `config.php` الحالي كما هو.
4. لا ترفع أي ملف يحتوي بيانات قاعدة البيانات إلى GitHub.

ملف الخط مرخص وفق SIL Open Font License 1.1، وتوجد نسخة الترخيص في
`public/reseller/assets/fonts/OFL-1.1.txt`.

## كود الدخول

- الشكل الحالي: `HULK-AB12-CD34` حتى `HULK-AB12-CD34-EF56`.
- يكتب الموزع من 8 إلى 12 حرفًا أو رقمًا، مع حرف إنجليزي كبير واحد على الأقل.
- تقبل الـAPI الأكواد القديمة ذات الحمولة المكونة من 16 خانة حتى لا تتوقف الحسابات السابقة.
- عند تغيير الكود يتوقف الكود السابق فورًا.

## صيغة API

```http
POST /api/reseller/resolve/
Content-Type: application/json

{"code":"HULK-AB12-CD34"}
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

## الأمان

- كلمات المرور محفوظة كـ hashes فقط.
- كل عمليات الإدارة والموزع تستخدم جلسات منفصلة وCSRF.
- سياسة المحتوى تسمح فقط بالصور والخطوط والسكريبتات المحلية.
- لا توجد مفاتيح API أو بيانات موزعين أو بيانات قاعدة بيانات داخل السورس.
