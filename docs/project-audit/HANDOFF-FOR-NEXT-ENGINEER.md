# HULK SA Android — تسليم المهندس التالي

## الملخص التنفيذي

v0.9.3.17 قابل للتجميع من السورس المعاد تكوينه، لكنه غير جاهز لـv1.0. Release غير موقع، lint يفشل، المصدر النهائي مولّد بدل وجوده مباشرة، وCompatibility Lab أكد 8 عيوب تنقل/Focus. الجاهزية الإجمالية 44/100.

## النسخة والفرع

- Repository: <https://github.com/azoozrm/-HULK-SA-Android>
- الفرع الرسمي: `phase-3-v0.9.3.0-adaptive-foundation`
- رأس التطبيق المختبر: `8db147faea8fae0290bf75d53b4194de2035880f`
- baseline: `v0.9.3.17-beta` للـDebug؛ base/release version هو `0.9.3.17`.
- لا Git tags.

## ما تم إنجازه فعليًا

- Debug وRelease/R8 build شُغلا بنجاح.
- 14/14 unit tests نجحت.
- ABI verifier نجح للـAPK/AAB Debug/Release.
- Compatibility Lab شُغّل على 9 profiles وأنتج HTML/Markdown/JSON وevidence.
- 132 app captures صالحة.
- Home snapshot/recommendations/downloads/focus/adaptive/player features موجودة وموصولة، لكنها جزئية الجودة.
- حزمة التدقيق في `docs/project-audit/`.

## غير المكتمل

- توقيع/تثبيت Release.
- lint clean.
- canonical source/Gradle Wrapper.
- 5 landscape navigation failures.
- 3 TV Search focus traps.
- authenticated backend/player/download E2E.
- physical ARM/OEM/API 23.
- Macrobenchmark/screenshot regression/process-death tests.

## آخر Build واختبارات

| البند | النتيجة |
|---|---|
| Debug compile/APK/AAB | PASS |
| Unit tests | PASS 14/14 |
| Release APK/AAB/R8 | PASS build، UNSIGNED |
| lintDebug | FAIL: 1 error، 32 warnings، 1 hint |
| Lab tooling tests | PASS 7/7 |
| Compatibility infrastructure | PASS |
| Compatibility product | FAIL |
| Crash/ANR داخل الجولة | 0/0 ضمن fixture captures |

## أهم المشاكل

- P0: Release غير موقع.
- P1: source reconstruction governance، landscape rail، TV Search focus، lint، cleartext credential risk، process-bound downloads، non-enforcing lab gate، غياب production E2E.
- P2/P3: راجع [KNOWN-ISSUES.md](KNOWN-ISSUES.md).

## الخطوة التالية الوحيدة

PR حوكمة مصدر بلا تغيير سلوك: materialize السورس الناتج الحالي في Git، أضف Gradle Wrapper 8.13 وWorkflow واحدًا يبني lint/unit/debug/release/ABI مباشرة من checkout، وأثبت parity.

## اقرأ أولًا

1. [PROJECT-STATE.md](PROJECT-STATE.md)
2. [KNOWN-ISSUES.md](KNOWN-ISSUES.md)
3. [BUILD-AND-RELEASE-AUDIT.md](BUILD-AND-RELEASE-AUDIT.md)
4. [COMPATIBILITY-RESULTS.md](COMPATIBILITY-RESULTS.md)
5. [ROADMAP-TO-V1.md](ROADMAP-TO-V1.md)
6. [TEST-STRATEGY.md](TEST-STRATEGY.md)
7. [ARCHITECTURE-AUDIT.md](ARCHITECTURE-AUDIT.md)
8. [project-state.json](project-state.json)

## الأوامر

```bash
AUDIT_OUT="$(mktemp -d)/project"
bash qa/compatibility/prepare-project.sh "$AUDIT_OUT"
cd "$AUDIT_OUT"
gradle --no-daemon --console=plain \
  clean :app:compileDebugKotlin testDebugUnitTest \
  :app:assembleDebug :app:bundleDebug \
  -PHULK_PORTAL_URL=https://example.invalid
gradle --no-daemon --console=plain :app:lintDebug
gradle --no-daemon --console=plain \
  :app:assembleRelease :app:bundleRelease \
  -PHULK_PORTAL_URL=https://example.invalid
```

```bash
python3 -m pip install -r qa/compatibility/requirements.txt
python3 -m unittest discover -s qa/compatibility/tests -v
python3 qa/compatibility/lab_config.py --validate
```

## GitHub Actions وArtifacts

- Run: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875>
- Report: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661831141>
- Lab APK: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661311514>
- Stability workflow: <https://github.com/azoozrm/-HULK-SA-Android/actions/workflows/build-v0947-stability-polish.yml>

## تحذيرات تمنع تكرار الأخطاء

- لا تبدأ من الصفر ولا تعيد التصميم.
- لا تبنِ `main` باعتباره الرسمي.
- لا تعتبر workflow الأخضر Product PASS؛ findings gate كان غير مفعل.
- لا تعتبر TV 720p/Series عيب تطبيق؛ الحالة `NOT TESTED`.
- لا تدّع نجاح Release install/E2E/performance.
- لا تعرض endpoint الفعلي أو credentials أو signing materials.
- لا تحذف reconstruction history قبل parity.

## COPY-PASTE CONTEXT

# HULK SA Android — NEW CHAT HANDOFF

هذا الملف مكتفٍ بذاته. لا تعتمد على أي محادثة سابقة. لا تبدأ المشروع من الصفر، ولا تعيد تصميم ما أُنجز، ولا تضف ميزة كبرى قبل إغلاق بوابات v1.0.

## الهوية المعتمدة

- Repository: <https://github.com/azoozrm/-HULK-SA-Android>
- Official branch: `phase-3-v0.9.3.0-adaptive-foundation`
- GitHub default branch عند التدقيق: `main`، لكنه ليس الفرع الرسمي الحالي.
- Baseline المطلوب: `v0.9.3.17-beta`.
- الحقيقة في Gradle: base `0.9.3.17`, versionCode `61`; Debug فقط يضيف `-beta`.
- لا يوجد Git Tag لأي إصدار وقت التدقيق.
- Application/Compatibility Lab audited head: `8db147faea8fae0290bf75d53b4194de2035880f`.
- Audit delivery commit: الـCommit الذي يحتوي هذا الملف، وهو HEAD الفرع الرسمي عند التسليم. Git لا يمكنه تضمين SHA الخاص بالـCommit داخل محتواه لأن SHA يعتمد على المحتوى نفسه. احصل على SHA الدقيق من: <https://github.com/azoozrm/-HULK-SA-Android/commits/phase-3-v0.9.3.0-adaptive-foundation>. يجب أن يطابق رابط Commit المرفق في رسالة التسليم.

## ملخص تنفيذي

المشروع قابل للتجميع لكنه غير جاهز لـv1.0. Debug وRelease/R8 بُنيا فعليًا، و14/14 unit tests نجحت، وABI verification نجح. لكن Release APK/AAB غير موقعين، `lintDebug` يفشل بخطأ Media3 واحد، والسورس النهائي لا يوجد كمشروع Gradle مباشر بل يُولد من ZIP v0.9.1.20 وسلسلة 24 script.

Compatibility Lab شُغّل فعليًا على 9 emulator profiles. أنشأ 133 capture و692 evidence file. بعد مراجعة الأدلة، 132 capture تخص التطبيق؛ حالة TV 720p/Series التقطت Android TV Launcher وهي `NOT TESTED`. توجد 8 عيوب تطبيق حرجة مؤكدة: 5 navigation failures في Phone landscape و3 TV Search focus traps. لا Crash أو ANR مؤكدة في الجولة.

Overall v1.0 readiness: **44/100**.

## ما تغير في Compatibility Lab

أُنشئ/عُدل:

- `.github/workflows/compatibility-lab.yml`
- `qa/compatibility/.gitignore`
- `qa/compatibility/QaActivity.kt`
- `qa/compatibility/README.md`
- `qa/compatibility/__init__.py`
- `qa/compatibility/aggregate.py`
- `qa/compatibility/analyze.py`
- `qa/compatibility/gate.py`
- `qa/compatibility/lab_config.py`
- `qa/compatibility/make-blocked-result.py`
- `qa/compatibility/prepare-harness.py`
- `qa/compatibility/prepare-project.sh`
- `qa/compatibility/requirements.txt`
- `qa/compatibility/run-lab.py`
- `qa/compatibility/run-native-emulator.sh`
- `qa/compatibility/tests/test_lab.py`

استُبدلت/حُذفت منظومة QA السابقة:

- `.github/workflows/qa-v09317-emulator-matrix.yml`
- `.github/workflows/qa-v09317-real-account-e2e.yml`
- `qa/emulator/**`
- `qa/e2e/**`

نتيجة ذلك: المختبر الحالي أقوى في UI compatibility/evidence matrix، لكنه fixture-only ولا يحتوي authenticated production E2E.

## ملفات Audit التي أُنشئت

- `docs/project-audit/PROJECT-STATE.md`
- `docs/project-audit/ARCHITECTURE-AUDIT.md`
- `docs/project-audit/BUILD-AND-RELEASE-AUDIT.md`
- `docs/project-audit/COMPATIBILITY-RESULTS.md`
- `docs/project-audit/KNOWN-ISSUES.md`
- `docs/project-audit/ROADMAP-TO-V1.md`
- `docs/project-audit/TEST-STRATEGY.md`
- `docs/project-audit/HANDOFF-FOR-NEXT-ENGINEER.md`
- `docs/project-audit/NEW-CHAT-HANDOFF.md`
- `docs/project-audit/project-state.json`

لم يُعدل `app/src/main` أو Business Logic أو التصميم خلال Commit تقارير التدقيق.

## وصف Compatibility Lab

- يعيد تكوين سورس التطبيق الحالي بنفس سلسلة production.
- يحقن Activity وfixtures في `app/src/debug` فقط.
- لا يختبر Login؛ يفتح authenticated main shell ببيانات deterministic.
- يفتح Home, Live, Movies, Series, Search, Downloads, Settings.
- يلتقط Screenshot PNG، UI XML، Logcat، crash/system logs، window/activity، gfxinfo، meminfo.
- يفحص page markers، bounds، clipping heuristics، safe area، overlap، crash/ANR/render، navigation reachability، وTV D-pad focus.
- يبني HTML وMarkdown وJSON وGitHub Step Summary.
- Push run يسجل findings ولا يفشل بها افتراضيًا؛ manual dispatch يدعم `enforce_findings=true`.

## التشغيل والـArtifacts

- Compatibility run: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875>
- Aggregate report artifact: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661831141>
- Lab APK artifact: <https://github.com/azoozrm/-HULK-SA-Android/actions/runs/30287050875/artifacts/8661311514>
- Stability build workflow page: <https://github.com/azoozrm/-HULK-SA-Android/actions/workflows/build-v0947-stability-polish.yml>
- Compatibility workflow page: <https://github.com/azoozrm/-HULK-SA-Android/actions/workflows/compatibility-lab.yml>

Artifact التقرير مسجل صالحًا حتى 2026-08-26. Device/APK artifacts مسجلة حتى 2026-08-10.

## الأجهزة والدقات والنتائج الفعلية

كل profiles هي Android Emulators x86_64، وليست OEM hardware certification.

| الجهاز | API | الدقة/density | الحالات | النتيجة |
|---|---:|---|---:|---|
| Pixel 4a | 29 | 1080×2340 / 440dpi | 28/28 valid؛ portrait+landscape، font 1.0/1.3 | `PASS*` |
| Pixel 6 | 31 | 1080×2400 / 420dpi | 14/14 | `FAIL` — Downloads+Settings navigation في Landscape |
| Pixel 8 Pro | 35 | 1344×2992 / 480dpi | 14/14 | `FAIL` — Settings navigation في Landscape |
| Galaxy S24 Ultra profile | 35 | 1440×3120 / 560dpi | 14/14 | `FAIL` — Downloads+Settings navigation في Landscape |
| Pixel Tablet | 35 | 1600×2560 / 320dpi | 28/28؛ portrait+landscape، font 1.0/1.3 | `PASS*` |
| Nexus 9 | 28 | 1536×2048 / 320dpi | 14/14 | `PASS*` |
| Android TV 720p | 36 | 1280×720 / 213dpi | 6 valid من 7 | `FAIL`; Search focus trap؛ Series `NOT TESTED` |
| Android TV 1080p | 36 | 1920×1080 / 320dpi | 7/7 | `FAIL` — Search focus trap |
| Android TV 4K | 36 | 3840×2160 / 640dpi | 7/7 | `FAIL` — Search focus trap |

`PASS*` = لا critical product defect مؤكد في الجولة، لكن توجد advisories ولم يُختبر عتاد فعلي.

الصفحات التي التقطت لكل حالة صالحة: Home, Live, Movies, Series, Search, Downloads, Settings. الاستثناء الوحيد: TV 720p/Series `NOT TESTED`.

## النتائج الخام وتصحيحها

- Raw cases: 133.
- Valid app captures: 132.
- Raw critical findings: 10.
- Confirmed product critical findings: 8.
- Invalid critical findings: 2، كلاهما من نفس TV 720p/Series launcher capture.
- Warnings: 252.
- Crash: 0.
- ANR: 0.
- Infrastructure error raw counter: 0، لكنه أخفق في تصنيف launcher capture.

Warning counts:

- `high_emulator_jank`: 133؛ غير صالح كبوابة أداء.
- `text_at_display_edge`: 39.
- `slow_page_start`: 27.
- `interactive_overlap`: 24.
- `possible_text_clipping`: 23.
- `tv_safe_area`: 6.

لا تعتمد warnings البصرية كعيوب مؤكدة قبل مراجعة screenshot/XML. لا تعتمد jank على أنه فشل أداء؛ القياس Debug Emulator وبضعة frames.

## حالة Build وAPK/AAB

Toolchain:

- Gradle 8.13، بلا Wrapper في repo.
- AGP 8.13.2.
- Kotlin 2.2.21.
- Java/JVM 17.
- compileSdk/targetSdk 36.
- minSdk 23.
- module واحدة `:app`.

النتائج المشغلة:

- source reconstruction: `PASS`.
- `compileDebugKotlin`: `PASS`.
- `testDebugUnitTest`: `PASS`, 14/14.
- `assembleDebug`: `PASS`.
- `bundleDebug`: `PASS`.
- `lintDebug`: `FAIL`, 1 error + 32 warnings + 1 hint.
- `assembleRelease`: `PASS`.
- `bundleRelease`: `PASS`.
- R8/resource shrinking: `PASS`.
- Release APK/AAB signature: `FAIL/UNSIGNED`.
- signed Release install: `NOT TESTED`.

Hashes المحلية المؤقتة:

- Debug APK: `faca742c60caa7e151968569b95c53826245ba67cef1630e159cd0eab9728cf0`.
- Debug AAB: `47396d500c1f6c85246869846d3e5023e89f6a304eef293828b359c302b8dce9`.
- Unsigned Release APK: `9b4e535b8871db9aa8157b95291c81a1fa885507801b47f598d64641c92bf021`.
- Unsigned Release AAB: `ed4dec629b6d9d43473145137982d2a9645767bb148968822862f6fff50fe472`.

ABI verifier مرّ للـAPK/AAB Debug/Release بمجموعة دقيقة:

- `arm64-v8a`
- `armeabi-v7a`
- `x86_64`
- لا `x86`

## ما تأكد وجوده في التطبيق

- `HomeContentSnapshot` موصول ومستهلك في Home.
- Smart Home scoring من history/favorites/genre/category.
- `becauseYouWatched`, `suggested`, `personalizedLive`, popular/featured/continue watching.
- Home catalog compaction إلى 320 عنصرًا.
- FocusRequester وnavigation memory وD-pad handlers واسعة.
- Downloads UI وqueue/pause/resume/retry/priority/Wi-Fi/schedule/speed/ETA/integrity.
- Adaptive phone/tablet/TV + compact/medium/expanded + touch/remote.
- Xtream networking وPortalResolver.
- AES/GCM Android Keystore credential vault.
- Media3 ExoPlayer/HLS، tracks، next episode، live switch، player release.
- SharedPreferences history/favorites/download metadata.
- R8/resource shrinking.

هذه الميزات ليست كلها `COMPLETED`: Smart Home/Recommendations/Downloads/Focus/Performance/Adaptive كلها `PARTIAL` بسبب المشاكل أو فجوات الاختبار الموضحة أدناه.

## المشاكل المؤكدة

### P0

1. Release غير موقع؛ لا signingConfig أو CI signing path.

### P1

1. السورس النهائي مولد من ZIP + 24 scripts، وليس Gradle project canonical.
2. Landscape navigation يخفي Downloads/Settings في 5 حالات على Pixel 6/8/Galaxy profiles.
3. TV Search Focus trap على 720p/1080p/4K.
4. `lintDebug` يفشل عند `PlayerScreen.kt:1707` بسبب Media3 unstable API opt-in.
5. cleartext مسموح عالميًا؛ Xtream credentials تدخل query/path إذا كانت البوابة HTTP.
6. Downloads process-bound CoroutineScope بلا WorkManager/Foreground Service.
7. Compatibility findings لا تفشل CI افتراضيًا.
8. لا authenticated production E2E في HEAD الحالي.

### P2

1. Home cache لا يدخل `favorites` في invalidation؛ recommendations قد تتقادم.
2. `UserLibrary.replaceFavorites()` يستخدم synchronous `commit()`.
3. adaptive sizing يستخدم `Configuration.screenWidthDp/screenHeightDp` بدل window container.
4. TV 720p/Series false critical؛ الحالة غير مختبرة.
5. navigation audit swipe قد يفتح notification shade.
6. jank advisory غير صالح كبوابة.
7. startup يقرأ JSON/download metadata على main thread: خطر مشتبه به من الكود.
8. لا Android instrumentation أو screenshot regression tests.
9. version/tag/default-branch governance غير متسقة.
10. service endpoint build configuration مكرر في 22 Workflow؛ لم تُكتشف credentials أو keys.

### P3

1. 22 Workflows، وبعض الأسماء/الإصدارات غير متطابقة.
2. Gradle Wrapper مفقود.
3. compiler deprecations وشروط دائمًا true.
4. duplicate payload files وduplicate/unused icon resources.
5. MainShell/Player/DownloadRepository ملفات ضخمة.
6. README عنوان فقط.

## المشاكل المشتبه بها منفصلة

- startup jank/ANR عند history/download metadata كبيرة؛ لم يحدث ANR في المختبر.
- DownloadRepository scope ownership/leak بعد ViewModel destruction؛ لم يُعمل heap/lifecycle test.
- process kill/night schedule download failure متوقع معماريًا، لكن test runtime لم يُشغل.
- edge/clipping/overlap/safe-area warnings البالغ عددها 92 غير متعلقة بالأداء تحتاج مراجعة حالة بحالة.
- cleartext credential exposure لا يحدث على HTTPS؛ يجب التحقق من production portal policy دون كشف عنوانه.
- R8 runtime regression غير مثبت لأن Release unsigned لم يُثبت.

## أشياء لم يمكن التحقق منها

- signing key ownership وupgrade certificate.
- GitHub/Play Store release رسمي.
- signed/minified Release runtime.
- real login/catalog/backend.
- real playback/download.
- arm64/armeabi-v7a physical devices.
- API 23 minimum.
- Samsung/TCL/OEM firmware.
- HDR/codecs/subtitles/audio focus.
- process death/reboot/background constraints.
- long-run memory/leak/ANR.
- Store Data Safety/privacy/pre-launch report.
- performance SLA/Macrobenchmark.

## قيود وFalse Positives/Negatives

False positives مثبتة:

- TV Launcher حُسب app failure.
- notification shade contamination.
- 100% jank من عينة frames صغيرة.
- lazy edge/semantic overlap heuristics.

False negatives المحتملة:

- fixture bypasses backend/loading/errors.
- placeholder artwork.
- no media/download I/O.
- no ARM/OEM.
- no signed Release.
- no process death/accessibility/cutout/split-screen.

لذلك Confidence للمختبر الحالي: `MEDIUM`.

## درجات الجاهزية

| المحور | /100 |
|---|---:|
| Build | 68 |
| Installation | 55 |
| Phone | 57 |
| Tablet | 70 |
| Android TV | 43 |
| UI | 55 |
| Performance | 38 |
| Release signing | 5 |
| Store | 20 |
| Overall v1.0 | **44** |

## الخطة الرسمية حتى v1.0

1. استقرار البناء: materialize canonical source، Wrapper، workflow واحد، lint clean.
2. التثبيت والتوقيع: protected signing environment، signed APK/AAB، install/upgrade.
3. المعماريات: physical ARM/API minimum/runtime qualification.
4. التكييف: landscape rail، TV Search focus، Home cache، window/insets، warning triage.
5. الاختبارات: إصلاح lab classifier/gates، Compose UI/screenshots، Macrobenchmark، authenticated E2E، process/network/storage tests.
6. v1.0: feature freeze، protected tag، signed candidate، Store/pre-launch، staged rollout.
7. Features كبيرة بعد v1.0 فقط.

اقرأ `docs/project-audit/ROADMAP-TO-V1.md` لشروط القبول والمخاطر والاعتماديات.

## الخطوة التالية الوحيدة

أنشئ PR واحدًا لا يغير السلوك: ولّد السورس من `8db147f...`، ثبّت الناتج كمشروع Gradle canonical في Git، أضف Gradle Wrapper 8.13 وWorkflow واحدًا يشغّل clean/lint/unit/debug/release/ABI مباشرة من checkout، وأثبت parity قبل إزالة reconstruction.

لا تبدأ بإصلاح UI أو signing أو feature قبل قبول هذا PR، لأن كل هذه الأعمال يجب أن تُبنى فوق مصدر قابل للتتبع.

## الملفات التي يجب قراءتها أولًا

1. `docs/project-audit/PROJECT-STATE.md`
2. `docs/project-audit/KNOWN-ISSUES.md`
3. `docs/project-audit/BUILD-AND-RELEASE-AUDIT.md`
4. `docs/project-audit/COMPATIBILITY-RESULTS.md`
5. `docs/project-audit/ROADMAP-TO-V1.md`
6. `docs/project-audit/TEST-STRATEGY.md`
7. `docs/project-audit/ARCHITECTURE-AUDIT.md`
8. `docs/project-audit/project-state.json`

## أوامر البناء والاختبار الصحيحة للحالة الحالية

```bash
AUDIT_OUT="$(mktemp -d)/project"
bash qa/compatibility/prepare-project.sh "$AUDIT_OUT"
cd "$AUDIT_OUT"

gradle --no-daemon --console=plain \
  clean :app:compileDebugKotlin testDebugUnitTest \
  :app:assembleDebug :app:bundleDebug \
  -PHULK_PORTAL_URL=https://example.invalid

gradle --no-daemon --console=plain \
  :app:lintDebug

gradle --no-daemon --console=plain \
  :app:assembleRelease :app:bundleRelease \
  -PHULK_PORTAL_URL=https://example.invalid
```

`lintDebug` متوقع أن يفشل حاليًا حتى إصلاح P1-04. لا تستخدم placeholder لتشغيل production؛ configuration الحقيقية يجب أن تحقن من بيئة آمنة.

اختبارات أدوات المختبر:

```bash
python3 -m pip install -r qa/compatibility/requirements.txt
python3 -m unittest discover -s qa/compatibility/tests -v
python3 qa/compatibility/lab_config.py --validate
```

تشغيل المختبر كبوابة بعد إصلاح false positives:

```bash
gh workflow run compatibility-lab.yml \
  --ref phase-3-v0.9.3.0-adaptive-foundation \
  -f enforce_findings=true
```

## تحذيرات إلزامية

- لا تبدأ المشروع من الصفر.
- لا تعيد تصميم المنجز.
- لا تعتبر اسم Script/Commit دليل إنجاز؛ افحص الناتج الموصول.
- لا تستخدم `main` بدل الفرع الرسمي دون قرار merge موثق.
- لا توقع Debug artifact كتسليم production.
- لا تقل إن Release install أو E2E أو performance نجح؛ لم يُشغل.
- لا تعتبر GitHub run الأخضر product PASS؛ gate كان غير مفعل للـfindings.
- لا تعتبر TV 720p/Series FAIL للتطبيق؛ هو `NOT TESTED`.
- لا تعرض أو تنسخ Secrets أو بيانات دخول أو signing material أو service endpoint الفعلي.
- لا تحذف reconstruction history قبل إثبات canonical-source parity.
- لا تبدأ Feature كبيرة قبل إغلاق P0/P1 وإصدار v1.0 مستقر.
