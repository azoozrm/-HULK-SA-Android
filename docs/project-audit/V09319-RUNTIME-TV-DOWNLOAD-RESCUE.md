# HULK SA Android — v0.9.3.19 Runtime, TV and Download Rescue

تاريخ التشخيص: 2026-07-29 UTC

المرشح: `0.9.3.19` / `versionCode 63`

حزمة الإنتاج: `sa.hulksa.player`

## الأدلة المستلمة من Xiaomi

- نسخة الإنتاج ثبتت وفتحت وسجلت الدخول وشغلت واجهة التطبيق.
- شاشة البث أظهرت إطار فراغ أسود حول محتوى الصفحة وقصًا بصريًا عند طرفي شريط الفئات.
- تنزيل حلقة انتقل إلى `DOWNLOADING` بعد معرفة الحجم `261 MB`، لكنه بقي عند `0 MB / 261 MB`.

## السبب الجذري لهوية التثبيت

مرجع `v0.9.3.17-beta` المتاح هو Debug:

- applicationId: `sa.hulksa.player.dev`
- versionCode: `61`
- شهادة SHA-256: `39:72:70:54:35:A4:E7:D3:E2:05:A5:B3:02:7E:E4:24:C7:20:A9:B3:E7:45:A6:D5:F1:A9:B3:D6:33:1B:27:0B`

نسخة الإنتاج `v0.9.3.18`:

- applicationId: `sa.hulksa.player`
- versionCode: `62`
- شهادة الإنتاج SHA-256: `14:4E:05:48:DA:50:2A:A8:BD:06:0A:9A:28:0A:98:BB:9D:C6:76:33:87:BF:8B:20:EA:10:0B:5F:ED:65:2F:E0`

Android لا ينفذ upgrade بين applicationId أو شهادتين مختلفتين. لذلك ظهور
التطبيقين ليس فشلًا في APK الإنتاج؛ نسخة `.dev` القديمة تحتاج إزالة لمرة
واحدة. المرشح الجديد يبقي applicationId الإنتاج ويرفع versionCode إلى `63`
ليكون مؤهلًا لتحديث نسخة الإنتاج `62`.

## السبب الجذري لحواف Android TV

`LiveCatalogScreen` كان يضيف `23dp` أفقيًا و`18dp` رأسيًا حول الصفحة كاملة،
ثم كان `ReorderableLiveCategoryBar` يضيف `24dp` أفقيًا مرة أخرى. النتيجة
هامش فعلي يصل إلى `47dp` لشريط الفئات وإطار ظاهر حول محتوى البث.

الإصلاح المحدود:

- outer live-page padding: `8dp`.
- live category edge padding: `8dp`.
- إضافة قياس فعلي في Compatibility Lab بين `qa-tv-live-content` و
  `qa-tv-rail`.
- تصبح أي فجوة أكبر من `12dp` Finding حرجة.

لم يتغير ملف الشعار أو ألوانه أو طريقة رسمه.

## السبب الجذري لتوقف التنزيل عند 0%

مسار التنزيل كان:

1. يرسل probe باستخدام `Range: bytes=0-0`.
2. يعرف الحجم ودعم byte ranges.
3. يبدأ الطلب الحقيقي من الصفر بلا Range.
4. يستخدم `readTimeout(0)`، أي يسمح للاتصال الذي أرسل headers ثم توقف أن
   يبقى إلى أجل غير محدد.

هذا يطابق الحالة المرصودة: الحجم معروف والحالة `DOWNLOADING` لكن لا يصل أول
بايت.

الإصلاح المحدود:

- إرسال `Range: bytes=0-` من أول طلب عندما يثبت probe دعم الاستكمال.
- إبقاء `bytes=<offset>-` عند الاستئناف.
- مهلة 30 ثانية بين البايتات بدل الانتظار اللانهائي؛ وصول البيانات المستمر
  لا يتأثر.
- عند التوقف، يعيد مسار retry الحالي المحاولة بدل البقاء عند 0% للأبد.
- إضافة unit tests لطلب البداية والاستئناف والطلب العادي وسياسة المهلة.

## حالة التحقق

| البند | الحالة |
|---|---|
| إثبات سبب التطبيقين | `COMPLETED` |
| إصلاح أهلية تحديث الإنتاج | `IMPLEMENTED / SIGNED CI PENDING` |
| إصلاح حواف شاشة البث | `IMPLEMENTED / LAB + PHYSICAL PENDING` |
| بوابة قياس الحواف | `IMPLEMENTED / LAB PENDING` |
| إصلاح تعليق النقل عند 0% | `IMPLEMENTED / UNIT + RUNTIME PENDING` |
| Python lab tests | `PASS` |
| Android unit/build/lint/R8 | `PENDING CI` |
| Signed APK/AAB | `PENDING` |
| Xiaomi install-over-production | `PENDING` |
| تنزيل فعلي حتى تقدم البايتات | `PENDING` |

لا يعتمد الإصلاح نهائيًا ولا يدمج قبل اختبار APK الموقع على Xiaomi وتنفيذ
تنزيل فعلي يثبت انتقال العداد من صفر.
