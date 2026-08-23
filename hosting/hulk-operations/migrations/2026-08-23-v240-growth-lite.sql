SET NAMES utf8mb4;
SET time_zone = '+03:00';

INSERT IGNORE INTO app_settings (setting_key, setting_value) VALUES
    ('growth_enabled', '1'),
    ('growth_renewal_enabled', '1'),
    ('growth_renewal_title', 'التجديد والموقع'),
    ('growth_renewal_url', 'https://hulksa.com/'),
    ('growth_renewal_display_text', 'hulksa.com'),
    ('growth_renewal_qr_mode', 'AUTO'),
    ('growth_renewal_custom_qr_path', ''),
    ('growth_support_enabled', '1'),
    ('growth_support_title', 'الدعم الفني'),
    ('growth_support_url', 'https://wa.me/966506349935'),
    ('growth_support_display_text', '0506349935'),
    ('growth_support_qr_mode', 'AUTO'),
    ('growth_support_custom_qr_path', ''),
    ('growth_renewal_banner_enabled', '1'),
    ('growth_renewal_banner_days', '7');
