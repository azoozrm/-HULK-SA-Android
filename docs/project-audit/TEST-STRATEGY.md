# HULK SA Android — استراتيجية الاختبار

## الحكم الحالي

الاختبارات الحالية تثبت قابلية التجميع وبعض منطق parsing/classification،
وتقدم Compatibility Lab واسعًا للواجهة على Emulator. مرشح v0.9.3.20 يضيف
تنزيل loopback فعليًا للحارس، لكنه لا يثبت production readiness: لا توجد
Android instrumentation tests كاملة، ولا backend/playback/download production
E2E، ولا Macrobenchmark.

## ما يوجد فعليًا

### Unit tests في التطبيق

| suite | العدد | النتيجة المشغلة | ما تغطيه |
|---|---:|---|---|
| `AdaptiveUiClassifierTest` | 6 | `PASS` | phone/tablet/TV وcompact/medium/expanded/navigation classification |
| `ArtworkUrlTest` | 4 | `PASS` | artwork URL normalization |
| `PlaybackAndDownloadTest` | 4 | `PASS` | أجزاء محدودة من playback/download helper logic |
| **الإجمالي** | **14** | **14/14 PASS** | منطق JVM فقط |

الأمر شُغّل فعليًا ضمن Debug build:

```bash
gradle testDebugUnitTest
```

### اختبارات أدوات Compatibility Lab

17 Python unittest، ومنها:

- clean capture.
- external Android error dialog classification.
- out-of-bounds critical.
- ADB transient recovery.
- external error close target.
- matrix completeness/serialization.
- PNG dimension parsing.

النتيجة المشغلة: `7/7 PASS`.

```bash
python3 -m py_compile qa/compatibility/*.py qa/compatibility/tests/*.py
python3 -m unittest discover -s qa/compatibility/tests -v
bash -n qa/compatibility/prepare-project.sh
bash -n qa/compatibility/run-native-emulator.sh
```

### Compatibility Lab

- 9 emulator profiles.
- 133 planned captures؛ 132 valid app captures.
- التشغيل السابق: 7 صفحات و133 لقطة مخططة.
- مرشح v0.9.3.20: 8 صفحات و152 لقطة مخططة بعد إضافة «قائمتي».
- screenshots/XML/logcat and diagnostics.
- 8 confirmed product critical findings.
- 1 invalid capture produced 2 false critical findings.

التفاصيل في [COMPATIBILITY-RESULTS.md](COMPATIBILITY-RESULTS.md).

## ما لا تختبره الاختبارات الحالية

- Login production أو credential migration.
- catalog API production/parsing variants.
- Movie/Series details production.
- Media3 playback على stream فعلي.
- HLS/TS fallback، subtitles/audio tracks/codecs/HDR.
- download against production origin/integrity/storage/process death.
- signed/minified Release runtime.
- Compose semantics/navigation assertions.
- screenshot visual regression مقابل approved baseline.
- accessibility/TalkBack.
- hardware keyboard.
- true OEM behavior.
- arm64/armeabi-v7a runtime.
- API 23 minimum.
- low memory/storage/network/reboot.
- Store install split/upgrade.

## تقييم أدوات المختبر

الاختيار الحالي مناسب لاكتشاف layout/focus على Android runtime:

- ADB + UIAutomator hierarchy مفيدان خارج التطبيق ومن دون coupling.
- deterministic debug fixture يجعل اللقطات قابلة للمقارنة ولا يحتاج بيانات دخول.
- Android Emulator matrix يغطي geometry/API/density بتكلفة معقولة.

لكن لا توجد أداة واحدة تكفي. المعمارية الموصى بها متعددة الطبقات:

| الطبقة | الأداة الأنسب | السبب |
|---|---|---|
| Unit/domain | JUnit + coroutine test | سرعة وعزل |
| Compose behavior/focus | Compose UI Test + Espresso interoperability | semantics وFocusRequester وBack/IME |
| Visual regression | Roborazzi/Paparazzi للسرعة + device screenshots للـinsets/TV | golden diffs مع runtime checks |
| Isolation | Android Test Orchestrator | منع تسرب state بين journeys |
| Compatibility | المختبر الحالي بعد تصحيح analyzer | geometry/density/API/evidence |
| Performance | Macrobenchmark + Baseline Profile | startup/frame timing على Release-like |
| Physical devices | Firebase Test Lab أو مزود ARM/TV فعلي | OEM/codecs/ARM |
| Authenticated production smoke | workflow محمي بالـSecrets | backend/parser/player/download |
| Static/release | lint, R8, dependency/SBOM, ABI/signature/bundletool | جودة الحزمة |

Shot ليس الخيار الأول هنا لأن Compose وruntime TV focus هما الأهم. Paparazzi/Roborazzi لا يستبدلان emulator/device tests، لأنهما لا يثبتان D-pad/IME/system insets/playback.

## False Positives المعروفة

1. TV 720p/Series التقط Launcher وصُنّف كـempty app hierarchy.
2. navigation swipe فتح notification shade في دليل Pixel 6.
3. `high_emulator_jank` ظهر 133/133 من عينة صغيرة.
4. عناصر LazyRow الجزئية عند الحافة قد تُحسب edge clipping.
5. تداخل semantics parent/child قد يُحسب interactive overlap.
6. conservative text-height threshold قد يصنف glyph bounds الطبيعي كقص.

الإجراء:

- verify foreground package.
- associate findings with page marker.
- distinguish container/child overlap.
- exclude intentional partially visible lazy items.
- move performance خارج compatibility status.
- require manual confirmation أو approved baseline للتحذيرات البصرية.

## False Negatives المحتملة

1. fixture يتجاوز loading/error/backend states.
2. الصور placeholders ثابتة ولا تمثل artwork dimensions الحقيقية.
3. لا network latency/failure خارج سيناريو تنزيل loopback المحدد.
4. تنزيل المختبر فعلي محليًا، لكنه لا يمثل production origin؛ player ليس فعليًا.
5. emulator profile لا يحاكي Samsung/TCL firmware.
6. كل runtime images x86_64.
7. screenshots لا تختبر overscan الفعلي.
8. لم يُشغل font 1.3 على كل الأجهزة ولا 1.5/2.0.
9. لا cutout/fold/split-screen.
10. لا process recreation لأن Manifest يعالج config changes.

## بوابات الاختبار المقترحة

### Gate A — لكل PR

- source governance check.
- Kotlin compile.
- unit tests.
- lint.
- debug build.
- analyzer unit tests.
- focused Compose UI tests.

زمن مستهدف: أقل من 15 دقيقة بعد تثبيت canonical source/cache.

### Gate B — PR يغير UI/navigation

- screenshot golden affected components.
- small phone portrait/landscape.
- tablet portrait/landscape.
- TV 1080p D-pad.
- `enforce_findings=true` بعد تصحيح false positives.

### Gate C — Nightly

- matrix الكامل 9 profiles.
- API minimum/current.
- network shaping.
- process death/download recovery.
- Macrobenchmark sample.
- artifact retention/report trend.

### Gate D — Release candidate

- signed/minified Release.
- physical arm64 phone/tablet/TV.
- authenticated smoke.
- real playback/download.
- full Macrobenchmark.
- 1h+ playback/download soak.
- install/upgrade/rollback.
- Store/bundle pre-check.

## الاختبارات الضرورية قبل v1.0

### P0/P1 tests

1. Release signing/install/upgrade.
2. Landscape rail reachability لكل destination.
3. TV Search field→result→rail→Back focus journey.
4. HTTP/HTTPS credential transport test.
5. download process kill/reboot/constraints.
6. lint clean.
7. authenticated catalog/details/player/download smoke.

### Layout matrix

- Phone: 360×640dp، 393×852dp، 480dp+ landscape.
- Tablet: 600/840 breakpoints، portrait/landscape.
- TV: 1280×720، 1920×1080، 3840×2160 مع title-safe.
- font scale: 1.0، 1.3، 1.5 على critical screens.
- density: mdpi/xhdpi/xxhdpi/xxxhdpi.
- RTL وLTR إن كان المحتوى المختلط مدعومًا.
- cutout/gesture/3-button nav.

### Journeys

- Home → featured/details → play → Back.
- Live → category → channel → switch → Back.
- Movies/Series → category/search/details.
- Series → season/episode/next episode.
- Search TV → IME → result → Back.
- Download enqueue/pause/resume/priority/Wi-Fi/schedule/complete/delete.
- Settings diagnostics/logout.
- rotation/resize أثناء كل screen رئيسية.

## معايير الأداء المقترحة

لا تُعتمد أرقام المختبر الحالية كـSLA. يبدأ الفريق baseline من القياس ثم يقر budget واقعيًا.

القياس الأدنى:

- Release-like/profileable variant.
- 10 iterations على الأقل.
- cold/warm/hot startup منفصلة.
- FrameTimingMetric لscroll grids وفتح details.
- MemoryUsageMetric للـHome الكبير وPlayer.
- CPU/battery/network sample للتنزيل والبث.
- low-tier ARM phone وTV.

لا يسجل performance `PASS` حتى توجد results artifact بأرقام iterations/device/build SHA.

## أوامر التشغيل الصحيحة

### إعادة تكوين المصدر الحالي

```bash
AUDIT_OUT="$(mktemp -d)/project"
bash qa/compatibility/prepare-project.sh "$AUDIT_OUT"
cd "$AUDIT_OUT"
```

السكريبت يضيف debug-only fixture. Release source set لا يتضمنه.

### Build واختبارات JVM

يتطلب JDK 17 وGradle 8.13 وAndroid SDK 36:

```bash
gradle --no-daemon --console=plain \
  clean lint testDebugUnitTest \
  assembleDebug bundleDebug assembleRelease bundleRelease \
  -PHULK_PORTAL_URL=https://example.invalid
```

حاليًا هذا الأمر سيفشل عند lint إلى أن يُغلق `P1-04`. لا تستخدم placeholder لتشغيل production؛ بيئة Release يجب أن تحقن configuration المحمية.

### اختبارات أدوات المختبر

```bash
python3 -m pip install -r qa/compatibility/requirements.txt
python3 -m unittest discover -s qa/compatibility/tests -v
python3 qa/compatibility/lab_config.py --validate
```

### تشغيل GitHub Compatibility Lab كبوابة

```bash
gh workflow run compatibility-lab.yml \
  --ref phase-3-v0.9.3.0-adaptive-foundation \
  -f enforce_findings=true
```

لا يُشغّل تعديل `docs/project-audit/**` المختبر تلقائيًا لأن path filters لا تشمل docs.

## تعريف PASS النهائي

اختبار لا يسجل `PASS` إلا إذا:

- شُغّل الأمر/السيناريو فعليًا.
- build SHA/device/API مسجلة.
- artifact/evidence موجود.
- لا infrastructure contamination.
- expected assertions واضحة.
- flaky retry لا يخفي failure؛ يُسجل retry count.
- البيانات الحساسة محجوبة.

وجود marker نصي في السورس أو نجاح Workflow step لا يساوي نجاح behavior.
