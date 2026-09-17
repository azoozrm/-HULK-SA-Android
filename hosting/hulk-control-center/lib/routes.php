<?php

declare(strict_types=1);

function cc_modules(): array
{
    return [
        'dashboard' => [
            'label' => 'نظرة عامة',
            'title' => 'نظرة عامة',
            'description' => 'ملخص تشغيلي موحّد لحالة تطبيق HULK SA.',
            'icon' => 'dashboard',
            'group' => 'overview',
        ],
        'live-users' => [
            'label' => 'المستخدمون الآن',
            'title' => 'المستخدمون الآن',
            'description' => 'متابعة المستخدمين المتصلين وحالة نشاطهم.',
            'icon' => 'users',
            'group' => 'audience',
        ],
        'sessions' => [
            'label' => 'الجلسات',
            'title' => 'الجلسات',
            'description' => 'سجل جلسات التطبيق وأوقات الاتصال.',
            'icon' => 'clock',
            'group' => 'audience',
        ],
        'devices' => [
            'label' => 'الأجهزة',
            'title' => 'الأجهزة',
            'description' => 'الأجهزة والمنصات وإصدارات النظام والتطبيق.',
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
            'description' => 'عرض وإدارة أكواد الدخول المرتبطة بالموزعين.',
            'icon' => 'key',
            'group' => 'reseller',
        ],
        'hosts' => [
            'label' => 'الهوستات',
            'title' => 'الهوستات',
            'description' => 'الهوستات الحالية وعلاقاتها بالموزعين.',
            'icon' => 'server',
            'group' => 'reseller',
        ],
        'releases' => [
            'label' => 'الإصدارات والتحديثات',
            'title' => 'الإصدارات والتحديثات',
            'description' => 'الإصدارات وسياسة التحديث والحد الأدنى المدعوم.',
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
            'label' => 'مفاتيح المميزات',
            'title' => 'مفاتيح المميزات',
            'description' => 'التحكم المركزي في توفر مميزات التطبيق.',
            'icon' => 'toggle',
            'group' => 'operations',
        ],
        'growth' => [
            'label' => 'النمو والتجديد والدعم',
            'title' => 'النمو والتجديد والدعم',
            'description' => 'إعدادات التجديد والدعم ومسارات النمو.',
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
            'label' => 'التشخيصات',
            'title' => 'التشخيصات',
            'description' => 'أعطال التطبيق المصنفة والقابلة للمعالجة.',
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
            'label' => 'سجل الإدارة',
            'title' => 'سجل الإدارة',
            'description' => 'أثر واضح للعمليات الإدارية داخل المركز.',
            'icon' => 'audit',
            'group' => 'system',
        ],
        'settings' => [
            'label' => 'الإعدادات',
            'title' => 'الإعدادات',
            'description' => 'إعدادات مركز التحكم والتطبيق.',
            'icon' => 'settings',
            'group' => 'system',
        ],
    ];
}

function cc_navigation_groups(): array
{
    return [
        'overview' => '',
        'audience' => 'المستخدمون',
        'reseller' => 'الوصول والتوزيع',
        'operations' => 'تشغيل التطبيق',
        'insights' => 'المراقبة والتحليل',
        'system' => 'النظام',
    ];
}

function cc_resolve_module(?string $requested): array
{
    $modules = cc_modules();
    $key = trim((string) $requested);
    if ($key === '') {
        $key = 'dashboard';
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
