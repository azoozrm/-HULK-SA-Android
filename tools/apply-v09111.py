#!/usr/bin/env python3
from pathlib import Path
import sys

root = Path(sys.argv[1])

def rw(rel, fn):
    p = root / rel
    text = p.read_text()
    new = fn(text)
    if new == text:
        raise SystemExit(f'No change applied to {rel}')
    p.write_text(new)

rw('app/build.gradle.kts', lambda t: t.replace('versionCode = 32', 'versionCode = 33').replace('versionName = "0.9.1.10"', 'versionName = "0.9.1.11"'))

def engine(t):
    t = t.replace(
        'val failedEndpoints = endpoints.filterNot(DiagnosticEndpoint::success)',
        'val failedEndpoints = endpoints.filter { it.kind == "api" && !it.success }'
    )
    t = t.replace(
        'title = "واجهات لم تستجب للفحص",',
        'title = "واجهات API لم تستجب للفحص",'
    )
    t = t.replace(
        'action = "اعد الفحص ثم راجع السيرفر اذا تكرر الفشل.",',
        'action = "اعد الفحص تلقائيا، ثم راجع الشبكة او السيرفر اذا تكرر فشل واجهات API الاساسية.",'
    )
    t = t.replace(
        'val endpointFailureCount = endpoints.count { !it.success }',
        'val endpointFailureCount = endpoints.count { it.kind == "api" && !it.success }'
    )
    t = t.replace(
        'val score = (100 - criticalCount * 18 - warningCount * 6 - endpointFailureCount * 5).coerceIn(0, 100)',
        'val score = (100 - criticalCount * 18 - warningCount * 6 - endpointFailureCount * 5).coerceIn(0, 100) // اختبارات البث غير الحاسمة وHTTP لا تخصم'
    )
    t = t.replace(
        'else -> CapabilityStatus.UNSTABLE\n        },\n        details = when {\n            probe == null -> "لم تتوفر عينة للاختبار."\n            probe.success -> "وصلت بيانات من المسار خلال ${probe.latencyMs}ms."\n            else -> "تعذر التحقق من المسار: ${probe.errorMessage ?: "سبب غير معروف"}."',
        'else -> CapabilityStatus.PARTIAL\n        },\n        details = when {\n            probe == null -> "لم تتوفر عينة للاختبار، ولا تعتبر مشكلة في التطبيق."\n            probe.success -> "وصلت بيانات من المسار خلال ${probe.latencyMs}ms."\n            else -> "تعذر حسم دعم المسار من عينة الفحص (${probe.errorMessage ?: "سبب غير معروف"})، ولا تخصم النتيجة من التقييم."'
    )
    t = t.replace(
        'progress(100, "اكتمل فحص غرفة العمليات وبناء خريطة المميزات")',
        'progress(100, "اكتمل التحليل الهندسي وفصل مشاكل السيرفر والتطبيق والشبكة")'
    )
    return t

rw('app/src/main/java/sa/hulksa/player/data/ServerDiagnosticsEngine.kt', engine)

def ui(t):
    t = t.replace('غرفة العمليات الهندسية V2', 'غرفة العمليات الهندسية V3')
    t = t.replace(
        'نتائج فعلية من السيرفر والجهاز والشبكة بدون معاقبة HTTP',
        'تصنيف هندسي يفصل API والبث والجهاز والشبكة بدون معاقبة HTTP او الاختبارات غير الحاسمة'
    )
    return t

rw('app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt', ui)
print('Applied v0.9.1.11 operations room classification upgrade')
