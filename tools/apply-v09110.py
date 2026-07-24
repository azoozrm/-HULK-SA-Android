#!/usr/bin/env python3
from pathlib import Path
import sys
root=Path(sys.argv[1])

def rw(rel, fn):
    p=root/rel
    t=p.read_text()
    n=fn(t)
    if n==t:
        raise SystemExit(f'No change applied to {rel}')
    p.write_text(n)

rw('app/build.gradle.kts', lambda t: t.replace('versionCode = 31','versionCode = 32').replace('versionName = "0.9.1.9"','versionName = "0.9.1.10"'))

def engine(t):
    old='''        if (!base.isHttps) {\n            issues += DiagnosticIssue(\n                id = "unencrypted_portal",\n                title = "اتصال السيرفر غير مشفر",\n                severity = DiagnosticSeverity.WARNING,\n                details = "بوابة السيرفر تعمل عبر HTTP، لذلك بيانات الاتصال لا تنتقل عبر TLS.",\n                action = "يفضل توفير HTTPS من جهة السيرفر قبل اطلاق النسخة العامة.",\n            )\n        }\n'''
    if old not in t:
        raise SystemExit('HTTP warning block not found')
    t=t.replace(old,'')
    old2='''        capabilities += CapabilityFinding(\n            id = "secure_transport",\n            title = "اتصال مشفر HTTPS",\n            status = if (base.isHttps) CapabilityStatus.SUPPORTED else CapabilityStatus.UNSUPPORTED,\n            details = if (base.isHttps) "بوابة السيرفر تستخدم TLS." else "بوابة السيرفر تعمل عبر HTTP.",\n            evidence = "المضيف: ${base.host}",\n        )\n'''
    new2='''        capabilities += CapabilityFinding(\n            id = "portal_protocol",\n            title = "بروتوكول بوابة IPTV",\n            status = CapabilityStatus.SUPPORTED,\n            details = if (base.isHttps) {\n                "البوابة تعمل عبر HTTPS. هذا تحسين اختياري ولا يغير دعم ميزات IPTV."\n            } else {\n                "البوابة تعمل عبر HTTP، وهو الوضع الشائع في مزودي IPTV ولا يخصم من التقييم."\n            },\n            evidence = "${base.scheme.uppercase(Locale.US)} • المضيف: ${base.host}",\n        )\n'''
    if old2 not in t:
        raise SystemExit('secure transport capability block not found')
    t=t.replace(old2,new2)
    t=t.replace('progress(70, "اختبار عينات البث بدون تشغيل كامل")','progress(70, "غرفة العمليات: اختبار عينات البث وتحليل سبب الفشل")')
    t=t.replace('progress(100, "اكتمل الفحص وبناء خريطة المميزات")','progress(100, "اكتمل فحص غرفة العمليات وبناء خريطة المميزات")')
    return t
rw('app/src/main/java/sa/hulksa/player/data/ServerDiagnosticsEngine.kt', engine)

def ui(t):
    t=t.replace('Text("مركز الفحص والتشخيص", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)',
                'Text("غرفة العمليات الهندسية V2", color = colors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)')
    t=t.replace('DiagnosticsSectionTitle("مصفوفة القدرات", "النتائج مبنية على دليل فعلي من الفحص")',
                'DiagnosticsSectionTitle("مصفوفة القدرات", "نتائج فعلية من السيرفر والجهاز والشبكة بدون معاقبة HTTP")')
    return t
rw('app/src/main/java/sa/hulksa/player/ui/screens/MainShellScreen.kt', ui)
print('Applied v0.9.1.10 operations room protocol-aware diagnostics upgrade')
