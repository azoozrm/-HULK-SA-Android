# HULK SA Android — تدقيق البنية

الرأس المدقق: `8db147faea8fae0290bf75d53b4194de2035880f`
السورس المفحوص: الناتج الفعلي لسلسلة التكوين حتى v0.9.3.17، لا أسماء الملفات التاريخية فقط.

## النتيجة

التطبيق Single-module Compose application ببنية عملية صغيرة: `AndroidViewModel + StateFlow` في الأعلى، Repository/client classes للبيانات، وCompose screens. لا توجد Clean Architecture أو طبقات modules مستقلة، ولا Navigation component أو DI framework أو Room/DataStore/WorkManager.

البنية قابلة للفهم والتجميع، لكنها أصبحت مركزة في ملفات ضخمة وتخلط state/navigation/storage/download scheduling داخل ViewModel واحد. أكثر عيبين معماريين تأثيرًا قبل v1.0 هما:

1. السورس النهائي مولّد من أدوات بدلاً من وجوده مباشرة في Git.
2. التنزيلات Coroutine process-bound وليست durable background work.

## الوحدات والحجم

- Gradle modules: `:app` فقط.
- Kotlin production files: 23.
- إجمالي Kotlin production: 11,525 سطرًا.

أكبر الملفات:

| الملف | الأسطر | الملاحظة |
|---|---:|---|
| `ui/screens/MainShellScreen.kt` | 2,727 | التنقل وكل شاشات main تقريبًا وHome/Live/Search/Downloads/Settings |
| `ui/screens/PlayerScreen.kt` | 1,762 | player state وcontrols وpanels وchannel browser |
| `data/DownloadRepository.kt` | 1,082 | persistence/scheduler/network/files/integrity في class واحدة |
| `HulkViewModel.kt` | 820 | auth/catalog/navigation/details/playback/download orchestration |
| `data/ServerDiagnosticsEngine.kt` | 748 | network/capability/stream diagnostics |

هذه ليست مشكلة crash مثبتة بذاتها، لكنها تزيد تكلفة التغيير والاختبار واحتمال regression.

## خريطة الحزم الفعلية

| الحزمة/المسار | المسؤولية |
|---|---|
| `sa.hulksa.player` | Activities، ViewModel، screen/destination state |
| `model/Models.kt` | كل DTO/state models وenums |
| `data/HulkRepository.kt` | façade يربط resolver/client/vault/diagnostics |
| `data/XtreamClient.kt` | Xtream API parsing، catalog/details/episodes/playback URL |
| `data/PortalResolver.kt` | HTTPS remote config ثم cached portal ثم compiled fallback |
| `data/UserLibrary.kt` | favorites/history عبر SharedPreferences JSON |
| `data/DownloadRepository.kt` | download queue/files/network/resume/integrity |
| `data/ServerDiagnosticsEngine.kt` | فحص API/streams/device capabilities |
| `security/CredentialVault.kt` | Android Keystore AES/GCM envelope |
| `ui/HulkApp.kt` | screen switch وربط callbacks |
| `ui/adaptive/AdaptiveUi.kt` | device/window/input/navigation classification |
| `ui/components/HulkComponents.kt` | reusable Compose controls/cards/text field |
| `ui/screens/*` | Login/Main/Details/Series/Player |
| `ui/theme/*` | الألوان والخطوط والثيم |

## تدفق الحالة والتنقل

`HulkViewModel` يملك `MutableStateFlow<HulkUiState>` ويعرض `StateFlow`. `HulkApp` يجمعه ويبدل composable وفق `HulkScreen`:

- `LOGIN`
- `MAIN`
- `MOVIE_DETAILS`
- `SERIES`
- `PLAYER`

داخل `MAIN` توجد `MainDestination`:

- Home
- Live
- Movies
- Series
- Favorites
- Search
- Downloads
- Settings

لا توجد route graph أو saved-state handles. Back للتفاصيل والمسلسل مربوط بـ`BackHandler`، والـ Player يدير Back داخليًا. Back من main يعود لسلوك Activity الافتراضي.

`NavigationMemoryStore` يحتفظ بموضع row/item لكل destination داخل Composition عبر `remember`. يفيد Focus/scroll عند العودة، لكنه ليس محفوظًا عبر process death.

## المصادقة والجلسة

- `PortalResolver` يقرأ config HTTPS اختياريًا، ثم cached portal، ثم `BuildConfig.PORTAL_URL`.
- `XtreamClient.authenticate` يفحص Xtream `user_info`.
- username/password المحفوظان يُشفّران بـAES/GCM ومفتاح Android Keystore.
- backup معطل في Manifest.
- عند session invalid يحذف ViewModel الـvault ويرجع إلى Login.

لم يجد فحص السورس username/password ثابتين أو private keys. توجد service endpoint configuration مكررة في Workflows ولا تُعد دليل credential، لكنها يجب أن تُدار خارج YAML.

## Networking

- OkHttp مباشر؛ لا Retrofit.
- JSON parsing يدوي بـ`org.json`.
- API calls تعمل داخل `Dispatchers.IO`.
- error mapping يميز invalid credentials، subscription inactive، HTTP، network، invalid response، وHTML challenge/block page.
- player يستخدم Media3 `DefaultHttpDataSource`.

مخاطرة أمنية مؤكدة بالتصميم:

- Manifest وnetwork security يسمحان cleartext لكل النطاقات.
- Xtream API يضع username/password في query parameters.
- stream URL يضعهما في path segments.
- إذا كانت بوابة المستخدم HTTP، يمكن للشبكة رؤية credentials والمحتوى؛ وإذا كانت HTTPS فالتشفير النقلـي موجود، لكن URLs قد تبقى عرضة للتسجيل في طبقات أخرى.

لا يوجد certificate pinning. عدم وجود pinning ليس عيبًا تلقائيًا، أما cleartext credentials فهو مانع أمني قبل Release production ما لم يكن هناك استثناء موثق ومقيد.

## التخزين المحلي

| البيانات | الآلية |
|---|---|
| credentials | SharedPreferences payload مشفر بمفتاح Android Keystore |
| favorites | SharedPreferences `StringSet` |
| history | JSON string داخل SharedPreferences، حد 100 |
| portal fallback | SharedPreferences |
| download metadata | JSON string داخل SharedPreferences |
| downloaded media | app external files dir ثم app files dir fallback |
| category ordering | SharedPreferences من UI layer |

لا توجد schema migrations/versioning واضحة لـhistory/download JSON. Parsing failure يعيد قائمة فارغة، ما قد يخفي تلف البيانات بدل إبلاغ المستخدم.

`UserLibrary.replaceFavorites()` يستخدم `commit()` synchronous. كذلك ViewModel constructor يقرأ ويفك JSON ويهيئ DownloadRepository على caller/main thread. لم يُقَس ANR لهذا، لكنه Main-thread risk حقيقي.

## الصفحة الرئيسية وSmart Home

`HomeContentSnapshot` موجود ويُستخدم فعليًا داخل `CinematicHomeScreen`:

- newest movies/series.
- continue watching.
- last live.
- `becauseYouWatched`.
- `suggested`.
- `personalizedLive`.
- popular movies/series.
- featured candidates.

`HulkViewModel.compactHomeCatalog()` يقلص catalogs الأكبر من 320 عنصرًا، مع الاحتفاظ بالمفضلة/history ثم الأحدث والأعلى تقييمًا، ويعمل الحساب على `Dispatchers.Default`.

عيب مؤكد: `NavigationMemoryStore` يعلن `homeFavorites` لكنه لا يقرأه في شرط reuse ولا يحدّثه. لذلك تغيير Favorite وحده يمكن أن يبقي recommendations/personalized live القديمة حتى يتغير catalog أو history.

التقييم:

- `HomeContentSnapshot`: `PARTIAL`.
- Smart Home: `PARTIAL`.
- Recommendations: `PARTIAL`.
- التحسينات ليست مجرد أسماء؛ توجد خوارزميات وصفوف UI فعلية، لكن invalidation والاختبارات ناقصان.

## Live / Movies / Series / Search

- Live يحمل catalog مستقلًا، فئات، preview، play/favorite، وcategory memory.
- Movies وSeries يستخدمان Lazy grids، بحثًا وفئات وcontinue/history.
- Details يجلب metadata عند الفتح ويربط play/download/favorite/related.
- Series يرتب الحلقات حسب season/episode ويحفظ history ويعرض downloads.
- Search يوحد catalogs الثلاثة ويفلتر name/year/genre/plot.

Search يستخدم `BasicTextField` عامًّا على الهاتف والتلفزيون. لا توجد TV-specific handoff بين IME/text field والنتائج. Compatibility Lab أثبت Focus trap على TV 720p/1080p/4K.

## Playback

- Media3 ExoPlayer وHLS.
- candidate fallback.
- resume prompt/progress history.
- live channel switch.
- audio/subtitle/video tracks.
- resize/speed/subtitle controls.
- TV control/focus handling.
- next episode flow.

إدارة المورد الأساسية سليمة في الكود:

- player يُنشأ بـ`remember(request)`.
- listener يزال في `onDispose`.
- progress يُحفظ.
- `player.release()` يُستدعى.

ما لم يُختبر:

- stream production فعلي.
- DRM إن وجد؛ لا يوجد تنفيذ DRM ظاهر.
- codecs وHDR/4K الفعليان.
- audio focus/interruption.
- long-run memory/leak.
- network loss/fallback.
- Release/R8 playback.

خطأ lint في `format.bitrate` يدل على استخدام Media3 unstable API من دون opt-in.

## Downloads

التنفيذ الحقيقي يشمل:

- queue وconcurrency.
- Wi-Fi only.
- schedule mode NOW/NIGHT.
- pause/resume/remove/retry.
- priorities.
- range resume.
- storage checks.
- speed وETA.
- part/final files.
- completion/integrity state.
- movie/episode UI progress.

لكن `DownloadRepository` ينشئ `CoroutineScope(SupervisorJob() + Dispatchers.IO)` خاصًا داخل repository:

- لا `WorkManager`.
- لا Foreground Service.
- لا Service في Manifest.
- لا notification permission/channel.
- لا close/cancel من ViewModel.
- Night schedule لا يملك OS alarm/worker؛ يعتمد على بقاء العملية ونداءات polling.

إذن redesign مرئي وعملي أثناء حياة العملية، لكنه ليس download manager موثوقًا في الخلفية. Android يستطيع قتل العملية، وعندها لا توجد ضمانة لاستمرار التنزيل أو بدء الجدول الليلي. metadata قد تتعافى عند التشغيل التالي، لكن ذلك ليس بديلًا عن durable execution.

## Adaptive UI وFocus

`AdaptiveUi.kt` يطبق:

- device class: phone/tablet/television.
- width classes: compact `<600dp`، medium `<840dp`، expanded.
- input modes: touch/remote.
- navigation type: bar/rail.
- focus highlights.

Foundation موصولة بـMainActivity/TvMainActivity وCompositionLocal، ولها 6 unit tests.

العيوب:

- الحساب يعتمد `LocalConfiguration.screenWidthDp/screenHeightDp`؛ lint يوصي بمعلومات container/window.
- `CinematicNavigationRail` هو `Column` غير scrollable مع 7 عناصر × 48dp، logo وpaddings/spacers. على ارتفاع Landscape الضيق لا يتسع.
- المختبر والكود يؤكدان عدم ظهور Downloads/Settings على Pixel 6 وPixel 8 Pro وGalaxy S24 Ultra في Landscape.
- TV Search لا يحرر focus من BasicTextField/IME.
- `sensorLandscape` على TV سيصبح أقل إلزامًا على Android 16 وفق lint، لذا يجب أن تستجيب الواجهة لتغيرات الحجم فعليًا.

## Compose وإعادة التركيب

إيجابيات:

- استخدام واسع لـLazyColumn/LazyRow/LazyVerticalGrid.
- `remember` لحساب filters/sorts.
- catalogs الكبيرة تُضغط للـHome.
- StateFlow مركزي وبسيط.
- artwork failure fallback.

مخاطر:

- `HulkUiState` كبير وأي copy قد يعيد تركيب شجرة واسعة.
- MainShell ملف واحد ضخم ويحتوي SharedPreferences وبعض business/display logic.
- Search على كل catalogs يمكن أن يعيد filter عند كل حرف؛ لا debounce أو background index.
- `NavigationMemoryStore.homeContent()` يجري sorting/scoring داخل composition عند invalidation.
- favorites/history JSON parsing وwrites في مسارات UI.

لا توجد Compose compiler metrics أو recomposition tracing أو Macrobenchmark؛ لذلك أي ادعاء “performance complete” غير متحقق.

## Lifecycle وANR وMemory

المؤكد:

- لا Crash/ANR في 132 capture صالحة من المختبر.
- PSS متاح في 91 حالة؛ median نحو 116.6 MiB وأقصى 142.0 MiB تقريبًا في Debug fixture.
- ExoPlayer يُحرر.

غير المتحقق:

- LeakCanary/heap dump.
- process death.
- 1h/4h playback soak.
- large catalog stress production.
- concurrent downloads under low storage/memory.
- foreground/background transitions.

مشتبه به من الكود:

- DownloadRepository scope قد يستمر بلا مالك واضح حتى موت العملية بعد ViewModel destruction.
- SharedPreferences/JSON/file checks أثناء ViewModel initialization يمكن أن تسبب startup work.
- لا Application-level download owner أو lifecycle contract.

## تحقق التحسينات من v0.9.1.20 إلى الحالي

| المجموعة | النتيجة |
|---|---|
| Phase 2 ABI qualification | موجودة فعليًا في Gradle ونجح verifier |
| Adaptive classification وTV entry | موجودة ومتصلة |
| Mobile navigation/gesture/player follow-ups | توجد مسارات drag/seek/back/focus في السورس؛ لم تُختبر كلها سلوكيًا |
| Navigation/category memory | موجودة في ViewModel وNavigationMemoryStore |
| Favorites/Home polish | موجودة؛ cache invalidation ناقص |
| RTL/login keyboard fixes | توجد RTL UI وIME handling في Login؛ المختبر لم يختبر login |
| Search/safe-area fixes | توجد paddings/focus changes؛ TV search ما زال مكسورًا |
| v0.9.3.16 retest scripts | مخرجاتها موجودة في السورس المبني؛ claims التاريخية ليست كلها runtime-verified |
| v0.9.3.17 navigation/player/details/download changes | markers والكود موجودة ويُجمع؛ Navigation landscape ما زال يفشل |

## توصيات بنيوية قبل v1.0

1. تثبيت السورس المولّد كمشروع Gradle canonical من دون تغيير behavior.
2. تقسيم MainShell وDownloadRepository تدريجيًا بعد وضع tests، لا إعادة تصميم شاملة.
3. نقل download execution إلى WorkManager/Foreground Service بحسب نوع المهمة.
4. إضافة TV-specific search focus/IME contract.
5. استبدال rail غير القابل للتمرير بتخطيط يضمن وصول كل destination.
6. جعل Home snapshot calculation خارج composition مع مفتاح cache يشمل favorites.
7. تمرير build configuration من بيئة محمية وتقليل cleartext إلى النطاقات الضرورية فقط.
8. إضافة lifecycle/process-death/release-R8 tests قبل أي feature كبيرة.
