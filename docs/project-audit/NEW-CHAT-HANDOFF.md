# HULK SA Android — NEW CHAT HANDOFF

هذا الملف مكتفٍ بذاته. لا تبدأ المشروع من الصفر، ولا تعيد تصميم ما أُنجز، ولا تضف ميزة كبيرة قبل إغلاق بوابات v1.0.

## الهوية المعتمدة

- Repository: `azoozrm/-HULK-SA-Android`.
- Official branch: `phase-3-v0.9.3.0-adaptive-foundation`.
- GitHub default branch هو `main` لكنه ليس الفرع الرسمي للعمل.
- Current official HEAD: `75853490d0fd9d7a0ed523eb30133288246094ba`.
- Current preparation version: `0.9.3.18`.
- versionCode: `62`.
- Debug يضيف `-beta`.
- applicationId لم يتغير.

## الحالة التنفيذية الحالية

Compatibility Lab foundation اكتمل وربط بالـPRs. تم دمج:

- PR #46: instrumentation foundation.
- PR #47: runtime/input compatibility coverage.
- PR #48: PR-triggered Compatibility Lab.
- PR #49: تحليل وتوثيق الجولة التاريخية.
- PR #50: responsive layout/device-class/navigation/safe-area foundation ورفع الإصدار إلى 0.9.3.18.
- PR #51: إغلاق Android TV Search focus trap.

## أحدث تشغيل معتمد

Compatibility Lab Run #31:

- Run ID: `30377208398`.
- PR head: `2af1356cd89bdd2d0f0cb7384791d8e8dfdf6449`.
- Conclusion: SUCCESS.
- Profiles: 9.
- Cases: 133/133.
- Warnings: 263.
- Infrastructure errors النهائية: 0.
- Crash/ANR المؤكد: 0/0.
- Raw Critical: 2.
- Confirmed Product Critical بعد مراجعة الأدلة: **0**.

الحالتان الخام ظهرتا في Android TV 1080p/Movies (`page_marker_missing` و`empty_hierarchy`). لقطة الدليل تعرض Android TV Launcher مع نافذة Google TV Shop وليست التطبيق، لذلك هما false positive/launcher contamination.

Android TV 720p احتاج retry بسبب Emulator عابر ثم أنتج 7/7 حالات صالحة.

Generated Source Snapshot Run #8 نجح أيضًا.

## ما أُغلق

- 5 Phone Landscape navigation defects القديمة.
- منع تحول الهاتف الأفقي إلى Tablet/Rail.
- Downloads/Settings reachability ضمن phone profiles.
- TV Search focus trap على 720p/1080p/4K.
- TV Search IME/D-pad conflict؛ `DPAD_DOWN` ينتقل لأول نتيجة.
- Product Critical المؤكد ضمن Compatibility fixture matrix أصبح 0.

## ما لم يُغلق

### Build/source governance

- السورس النهائي ما زال يُولد من ZIP v0.9.1.20 وسلسلة Scripts.
- لا يوجد Gradle project canonical مباشر معتمد في Git.
- Gradle Wrapper 8.13 غير موجود في المشروع الرسمي.
- لا يوجد Workflow حاكم واحد يبني مباشرة من checkout.
- parity بين reconstructed وcanonical source غير منفذ.

### lint

- `lintDebug` لم يُثبت clean على HEAD الحالي.
- الخطأ التاريخي Media3 unstable API في PlayerScreen يجب إعادة تشغيله والتحقق منه، لا تفترض اختفاءه.

### signing/release

- Release production موقع من HEAD الحالي غير موجود.
- signing configuration المحمي غير منفذ.
- certificate parity غير مثبت.
- clean install وupgrade من مرجع الاستقرار غير مختبرين.
- signed/minified R8 runtime غير مختبر.

### production/physical testing

- real login/catalog/playback/download E2E غير موجود.
- physical arm64/armeabi-v7a/API 23 غير مختبر.
- Samsung/TCL/OEM hardware غير مؤهل.
- process death/reboot/network/storage pressure غير مختبرة.
- Macrobenchmark/screenshot regression/accessibility غير مكتملة.

## تحذيرات Run #31

- `high_emulator_jank`: 133 — Advisory فقط، لا تستخدمه كبوابة أداء.
- `text_at_display_edge`: 42.
- `slow_page_start`: 39.
- `possible_text_clipping`: 22.
- `interactive_overlap`: 21.
- `tv_safe_area`: 6.

لا تعدل UI لمجرد Warning heuristic. راجع screenshot/XML وأثبت الأثر أولًا.

## الخطوة التالية الوحيدة

نفذ PR واحدًا بلا تغيير سلوك باسم canonical source governance:

1. أعد تكوين HEAD الحالي v0.9.3.18.
2. ثبّت الناتج كمشروع Gradle canonical داخل Git.
3. أضف Gradle Wrapper 8.13.
4. أنشئ Workflow واحدًا يشغل مباشرة:
   - clean.
   - lintDebug.
   - Unit Tests.
   - Debug APK/AAB.
   - Release APK/AAB.
   - R8/resource shrinking.
   - ABI verification.
5. أنشئ Artifact/report يثبت parity مع reconstruction output.
6. أبقِ ZIP والـScripts وWorkflows التاريخية مؤقتًا.
7. لا تحذف reconstruction history قبل قبول parity.

إذا فشل CI:

- افحص Logs.
- حدد السبب الدقيق.
- أصلح داخل نفس PR المنطقي.
- أعد التشغيل.
- لا تتوقف عند كل محاولة صغيرة.

## الترتيب بعد canonical source

1. lint clean.
2. protected signing.
3. signed Release APK/AAB.
4. signature/certificate verification.
5. clean install وupgrade.
6. R8 runtime qualification.
7. production E2E.
8. physical ARM/OEM/API 23.
9. performance/screenshots/process/network/storage.
10. Release Candidate ثم v1.0.

## قرارات خطرة تحتاج توقفًا

- حذف reconstruction history.
- تغيير applicationId.
- تغيير signing key.
- كسر upgrade path.
- تغيير معماري كبير.
- نشر Release عام.

## قواعد إلزامية

- لا تبدأ من الصفر.
- لا تعيد التصميم.
- لا تستخدم `main` بدل الفرع الرسمي.
- لا تعرض endpoint أو credentials أو signing materials.
- لا تعتبر Workflow أخضر Product PASS دون Artifacts.
- لا تعتبر Emulator شهادة OEM/ARM.
- لا تدّع نجاح signed Release أو production E2E أو performance قبل تشغيله.
- لا تبدأ Feature كبيرة قبل v1.0.

## ملفات المرجع

- `docs/project-audit/PROJECT-STATE.md`.
- `docs/project-audit/COMPATIBILITY-RESULTS.md`.
- `docs/project-audit/KNOWN-ISSUES.md`.
- `docs/project-audit/ROADMAP-TO-V1.md`.
- `docs/project-audit/HANDOFF-FOR-NEXT-ENGINEER.md`.
- `docs/project-audit/BUILD-AND-RELEASE-AUDIT.md`.
- `docs/project-audit/ARCHITECTURE-AUDIT.md`.
- `docs/project-audit/TEST-STRATEGY.md`.
- `docs/project-audit/project-state.json`.
