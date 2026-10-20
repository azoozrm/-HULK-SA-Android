<?php

declare(strict_types=1);

function cc_modules(): array
{
    return [
        'dashboard' => [
            'label' => 'النظرة العامة',
            'title' => 'مركز العمليات',
            'description' => 'الحالة الحالية للتطبيق وما يحتاج إلى انتباهك.',
            'icon' => 'dashboard',
            'group' => 'overview',
        ],
        'live-users' => [
            'label' => 'المتصلون الآن',
            'title' => 'المتصلون الآن',
            'description' => 'الاشتراكات والأجهزة التي أرسلت نشاطًا حديثًا إلى الخادم.',
            'icon' => 'users',
            'group' => 'audience',
        ],
        'sessions' => [
            'label' => 'الجلسات',
            'title' => 'سجل الجلسات',
            'description' => 'تاريخ الاتصالات وحالة كل جلسة وبياناتها المرتبطة.',
            'icon' => 'clock',
            'group' => 'audience',
        ],
        'devices' => [
            'label' => 'الأجهزة',
            'title' => 'أجهزة التطبيق',
            'description' => 'الأجهزة المسجلة وإصدارات النظام والتطبيق المستخدمة.',
            'icon' => 'device',
            'group' => 'audience',
        ],
        'resellers' => [
            'label' => 'الموزعون',
            'title' => 'الموزعون',
            'description' => 'إدارة الموزعين وحالتهم التشغيلية.',
            'icon' => 'briefcase',
            'group' => 'reseller',
        ],
        'access-codes' => [
            'label' => 'أكواد الدخول',
            'title' => 'أكواد الدخول',
            'description' => 'الأكواد الحالية المرتبطة بالموزعين وإدارتها.',
            'icon' => 'key',
            'group' => 'reseller',
        ],
        'hosts' => [
            'label' => 'الهوست والأكواد',
            'title' => 'الهوست والأكواد',
            'description' => 'الهوستات وأكواد الدخول الحالية وعلاقتها بالموزعين.',
            'icon' => 'server',
            'group' => 'reseller',
        ],
        'releases' => [
            'label' => 'تحديث التطبيق',
            'title' => 'تحديث التطبيق',
            'description' => 'ارفع النسخة، راجعها، وانشرها على رابطك الثابت.',
            'icon' => 'package',
            'group' => 'operations',
        ],
        'service' => [
            'label' => 'حالة الخدمة',
            'title' => 'حالة الخدمة',
            'description' => 'الحالة التشغيلية ورسائل الصيانة والتدهور.',
            'icon' => 'pulse',
            'group' => 'operations',
        ],
        'announcements' => [
            'label' => 'الإعلانات',
            'title' => 'الإعلانات',
            'description' => 'الإعلانات الموجّهة لمستخدمي التطبيق.',
            'icon' => 'bell',
            'group' => 'operations',
        ],
        'features' => [
            'label' => 'المميزات',
            'title' => 'مميزات التطبيق',
            'description' => 'تفعيل المميزات المعتمدة أو إيقافها مركزيًا.',
            'icon' => 'toggle',
            'group' => 'operations',
        ],
        'growth' => [
            'label' => 'التجديد والدعم',
            'title' => 'التجديد والدعم',
            'description' => 'إدارة تجربة التجديد ومسارات الدعم داخل التطبيق.',
            'icon' => 'trend',
            'group' => 'operations',
        ],
        'host-health' => [
            'label' => 'صحة الهوستات',
            'title' => 'صحة الهوستات',
            'description' => 'نتائج الفحص الدوري وتوفر الهوستات.',
            'icon' => 'heart',
            'group' => 'insights',
        ],
        'diagnostics' => [
            'label' => 'أعطال التطبيق',
            'title' => 'أعطال التطبيق',
            'description' => 'الأعطال المصنفة حسب النوع والإصدار والجهاز.',
            'icon' => 'diagnostic',
            'group' => 'insights',
        ],
        'analytics' => [
            'label' => 'التحليلات',
            'title' => 'التحليلات',
            'description' => 'اتجاهات الاستخدام واعتماد الإصدارات.',
            'icon' => 'chart',
            'group' => 'insights',
        ],
        'audit' => [
            'label' => 'النشاط الإداري',
            'title' => 'النشاط الإداري',
            'description' => 'ما تغيّر داخل المركز، ومن نفّذه، ومتى حدث.',
            'icon' => 'audit',
            'group' => 'system',
        ],
        'settings' => [
            'label' => 'إعدادات المركز',
            'title' => 'إعدادات مركز التحكم',
            'description' => 'ملخص آمن لحدود التشغيل والجهات المسؤولة عن التعديل.',
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
        'reseller' => 'الموزعون والوصول',
        'operations' => 'إدارة التطبيق',
        'insights' => 'المتابعة والتحليل',
        'system' => 'إدارة المركز',
    ];
}

function cc_main_navigation(): array
{
    return [
        'live-users' => [
            'label' => 'المتصلون الآن',
            'icon' => 'users',
        ],
        'announcements' => [
            'label' => 'الإعلانات',
            'icon' => 'bell',
        ],
        'hosts' => [
            'label' => 'الهوست والأكواد',
            'icon' => 'server',
        ],
        'releases' => [
            'label' => 'تحديث التطبيق',
            'icon' => 'package',
        ],
    ];
}

function cc_active_main_navigation(string $moduleKey): ?string
{
    return match ($moduleKey) {
        'live-users', 'sessions', 'devices' => 'live-users',
        'announcements' => 'announcements',
        'hosts', 'access-codes', 'resellers' => 'hosts',
        'releases', 'features' => 'releases',
        default => null,
    };
}

function cc_secondary_navigation_groups(): array
{
    $mainKeys = array_keys(cc_main_navigation());
    $serviceKeys = ['service', 'growth', 'settings'];
    $groups = [];
    foreach (cc_navigation_groups() as $groupKey => $groupLabel) {
        $items = [];
        foreach (cc_modules() as $moduleKey => $module) {
            if ($module['group'] !== $groupKey) {
                continue;
            }
            if (in_array($moduleKey, $mainKeys, true) || in_array($moduleKey, $serviceKeys, true)) {
                continue;
            }
            $items[$moduleKey] = $module['label'];
        }
        if ($items !== []) {
            $groups[$groupKey] = ['label' => $groupLabel, 'items' => $items];
        }
    }
    return $groups;
}

function cc_resolve_module(?string $requested): array
{
    $modules = cc_modules();
    $key = trim((string) $requested);
    if ($key === '') {
        $key = 'live-users';
    }

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
        'requested' => $key,
    ];
}
