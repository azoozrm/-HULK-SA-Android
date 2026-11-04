<?php

declare(strict_types=1);

function cc_modules(): array
{
    return [
        'dashboard' => [
            'label' => 'نظرة عامة',
            'title' => 'نظرة عامة',
            'description' => 'حالة التطبيق والاتصالات وأهم الإجراءات',
            'icon' => 'dashboard',
            'group' => 'overview',
        ],
        'live-users' => [
            'label' => 'المتصلون الآن',
            'title' => 'المتصلون الآن',
            'description' => 'ابحث عن حساب وافتح تفاصيل أجهزته',
            'icon' => 'users',
            'group' => 'audience',
        ],
        'sessions' => [
            'label' => 'السجلات',
            'title' => 'السجلات',
            'description' => 'متابعة ما حدث في التطبيق واللوحة',
            'icon' => 'clock',
            'group' => 'audience',
        ],
        'devices' => [
            'label' => 'الاجهزة',
            'title' => 'اجهزة التطبيق',
            'description' => 'الاجهزة المسجلة واصدارات النظام والتطبيق المستخدمة',
            'icon' => 'device',
            'group' => 'audience',
        ],
        'resellers' => [
            'label' => 'ادارة الهوستات',
            'title' => 'ادارة الهوستات',
            'description' => 'ادارة الهوستات والاكواد المرتبطة بها',
            'icon' => 'briefcase',
            'group' => 'reseller',
        ],
        'access-codes' => [
            'label' => 'اكواد الدخول',
            'title' => 'اكواد الدخول',
            'description' => 'اكواد الدخول وحالة كل كود',
            'icon' => 'key',
            'group' => 'reseller',
        ],
        'hosts' => [
            'label' => 'الهوستات والأكواد',
            'title' => 'الهوستات والأكواد',
            'description' => 'إدارة الهوستات وربط أكواد الدخول بها',
            'icon' => 'server',
            'group' => 'reseller',
        ],
        'releases' => [
            'label' => 'تحديث التطبيق',
            'title' => 'تحديث التطبيق',
            'description' => 'راجع الإصدار وسياسة التحديث من مكان واحد',
            'icon' => 'package',
            'group' => 'operations',
        ],
        'service' => [
            'label' => 'التحكم بالتطبيق',
            'title' => 'التحكم بالتطبيق',
            'description' => 'أدوات الإدارة اليومية في مكان واحد',
            'icon' => 'pulse',
            'group' => 'operations',
        ],
        'announcements' => [
            'label' => 'الرسائل',
            'title' => 'الرسائل',
            'description' => 'جهّز الرسائل التي تظهر للمستخدمين وراجعها قبل النشر',
            'icon' => 'bell',
            'group' => 'operations',
        ],
        'features' => [
            'label' => 'المميزات',
            'title' => 'مميزات التطبيق',
            'description' => 'تفعيل المميزات المعتمدة او ايقافها مركزيًا',
            'icon' => 'toggle',
            'group' => 'operations',
        ],
        'growth' => [
            'label' => 'التجديد والدعم',
            'title' => 'التجديد والدعم',
            'description' => 'ادارة تجربة التجديد ومسارات الدعم داخل التطبيق',
            'icon' => 'trend',
            'group' => 'operations',
        ],
        'host-health' => [
            'label' => 'صحة الهوستات',
            'title' => 'صحة الهوستات',
            'description' => 'نتائج الفحص الدوري وتوفر الهوستات',
            'icon' => 'heart',
            'group' => 'insights',
        ],
        'diagnostics' => [
            'label' => 'اعطال التطبيق',
            'title' => 'اعطال التطبيق',
            'description' => 'الاعطال المصنفة حسب النوع والاصدار والجهاز',
            'icon' => 'diagnostic',
            'group' => 'insights',
        ],
        'analytics' => [
            'label' => 'التحليلات',
            'title' => 'التحليلات',
            'description' => 'اتجاهات الاستخدام واعتماد الاصدارات',
            'icon' => 'chart',
            'group' => 'insights',
        ],
        'audit' => [
            'label' => 'النشاط الاداري',
            'title' => 'النشاط الاداري',
            'description' => 'ما تغيّر داخل المركز، ومن نفّذه، ومتى حدث',
            'icon' => 'audit',
            'group' => 'system',
        ],
        'settings' => [
            'label' => 'حدود التشغيل',
            'title' => 'حدود التشغيل',
            'description' => 'ملخص امن لحدود التشغيل والجهات المسؤولة عن التعديل',
            'icon' => 'settings',
            'group' => 'system',
        ],
    ];
}

function cc_navigation_groups(): array
{
    return [
        'overview' => 'الرئيسية',
        'audience' => 'النشاط المباشر',
        'reseller' => 'الهوستات والاكواد',
        'operations' => 'ادارة التطبيق',
        'insights' => 'المتابعة والتحليل',
        'system' => 'ادارة المركز',
    ];
}

function cc_main_navigation(): array
{
    return [
        'dashboard' => ['label' => 'نظرة عامة', 'icon' => 'dashboard'],
        'live-users' => ['label' => 'المتصلون الآن', 'icon' => 'users'],
        'hosts' => ['label' => 'الهوستات والأكواد', 'icon' => 'server'],
        'sessions' => ['label' => 'السجلات', 'icon' => 'clock'],
        'announcements' => ['label' => 'الرسائل', 'icon' => 'bell'],
        'releases' => ['label' => 'تحديث التطبيق', 'icon' => 'package'],
        'service' => ['label' => 'التحكم بالتطبيق', 'icon' => 'settings'],
    ];
}

function cc_active_main_navigation(string $moduleKey): ?string
{
    return match ($moduleKey) {
        'dashboard' => 'dashboard',
        'live-users' => 'live-users',
        'hosts', 'access-codes', 'resellers', 'host-health' => 'hosts',
        'sessions', 'devices', 'audit', 'diagnostics', 'analytics' => 'sessions',
        'announcements' => 'announcements',
        'releases' => 'releases',
        'service', 'features', 'growth', 'settings' => 'service',
        default => null,
    };
}

/** Every module has one home; related tools stay within its current section */
function cc_context_navigation(string $moduleKey): array
{
    return match (cc_active_main_navigation($moduleKey)) {
        'dashboard' => ['dashboard' => 'نظرة عامة'],
        'live-users' => [
            'live-users' => 'المتصلون الآن', 'sessions' => 'السجلات', 'devices' => 'الأجهزة',
        ],
        'hosts' => [
            'hosts' => 'الهوستات والأكواد', 'host-health' => 'حالة الهوستات',
        ],
        'sessions' => [
            'sessions' => 'السجلات', 'devices' => 'الأجهزة', 'audit' => 'سجل التغييرات',
            'diagnostics' => 'أعطال التطبيق', 'analytics' => 'الاستخدام',
        ],
        'announcements' => ['announcements' => 'الرسائل'],
        'releases' => ['releases' => 'تحديث التطبيق'],
        'service' => [
            'service' => 'التحكم بالتطبيق', 'features' => 'المميزات',
            'growth' => 'التجديد والدعم', 'settings' => 'حدود التشغيل',
        ],
        default => [],
    };
}

function cc_resolve_module(?string $requested): array
{
    $modules = cc_modules();
    $key = trim((string) $requested);
    if ($key === '') {
        $key = 'dashboard';
    }

    $requestedKey = $key;
    if (in_array($key, ['access-codes','resellers'], true)) $key = 'hosts';

    if (!array_key_exists($key, $modules)) {
        return [
            'key' => 'dashboard',
            'module' => $modules['dashboard'],
            'valid' => false,
            'requested' => $key,
        ];
    }

    return [
        'key' => $key,
        'module' => $modules[$key],
        'valid' => true,
        'requested' => $requestedKey,
    ];
}
