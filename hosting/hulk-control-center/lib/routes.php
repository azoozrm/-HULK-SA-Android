<?php

declare(strict_types=1);

function cc_modules(): array
{
    return [
        'dashboard' => [
            'label' => 'الرئيسية',
            'title' => 'الرئيسية',
            'description' => 'الحالة الحالية للتطبيق وما يحتاج الى انتباهك',
            'icon' => 'dashboard',
            'group' => 'overview',
        ],
        'live-users' => [
            'label' => 'المتصلون الان',
            'title' => 'المتصلون الان',
            'description' => 'الاشتراكات والاجهزة التي ارسلت نشاطًا حديثًا الى الخادم',
            'icon' => 'users',
            'group' => 'audience',
        ],
        'sessions' => [
            'label' => 'الجلسات',
            'title' => 'سجل الجلسات',
            'description' => 'تاريخ الاتصالات وحالة كل جلسة وبياناتها المرتبطة',
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
            'label' => 'الهوستات والاكواد',
            'title' => 'الهوستات والاكواد',
            'description' => 'اضف هوست ثم انشئ له كودا او اكثر',
            'icon' => 'server',
            'group' => 'reseller',
        ],
        'releases' => [
            'label' => 'تحديث التطبيق',
            'title' => 'تحديث التطبيق',
            'description' => 'ارفع النسخة، راجعها، وانشرها على رابطك الثابت',
            'icon' => 'package',
            'group' => 'operations',
        ],
        'service' => [
            'label' => 'حالة الخدمة',
            'title' => 'حالة الخدمة',
            'description' => 'الحالة التشغيلية ورسائل الصيانة والتدهور',
            'icon' => 'pulse',
            'group' => 'operations',
        ],
        'announcements' => [
            'label' => 'الرسائل',
            'title' => 'الرسائل',
            'description' => 'الاعلانات الموجّهة لمستخدمي التطبيق',
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
        'dashboard' => ['label' => 'الرئيسية', 'icon' => 'dashboard'],
        'hosts' => ['label' => 'الهوستات والاكواد', 'icon' => 'server'],
        'live-users' => ['label' => 'العملاء والاجهزة', 'icon' => 'users'],
        'announcements' => ['label' => 'الرسائل', 'icon' => 'bell'],
        'releases' => ['label' => 'تحديث التطبيق', 'icon' => 'package'],
        'service' => ['label' => 'اعدادات التطبيق', 'icon' => 'settings'],
    ];
}

function cc_active_main_navigation(string $moduleKey): ?string
{
    return match ($moduleKey) {
        'dashboard', 'diagnostics', 'analytics', 'audit' => 'dashboard',
        'hosts', 'access-codes', 'resellers', 'host-health' => 'hosts',
        'live-users', 'sessions', 'devices' => 'live-users',
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
        'dashboard' => [
            'dashboard' => 'نظرة عامة', 'analytics' => 'الاستخدام',
            'diagnostics' => 'اعطال التطبيق', 'audit' => 'سجل التغييرات',
        ],
        'hosts' => [
            'hosts' => 'الهوستات والاكواد', 'host-health' => 'حالة الهوستات',
        ],
        'live-users' => [
            'live-users' => 'المتصلون الان', 'sessions' => 'سجل الجلسات', 'devices' => 'الاجهزة',
        ],
        'service' => [
            'service' => 'الخدمة والصيانة', 'features' => 'مميزات التطبيق',
            'growth' => 'الدعم والتجديد', 'settings' => 'حدود التشغيل',
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
