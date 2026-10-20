from __future__ import annotations

from pathlib import Path
import re
import unittest


ROOT = Path(__file__).resolve().parents[1]
REPOSITORY = ROOT.parents[1]
OPS = REPOSITORY / "hosting" / "hulk-operations"
APP = REPOSITORY / "hosting" / "app" / "index.php"


class ReleaseR06ContractTests(unittest.TestCase):
    def read(self, path: Path) -> str:
        return path.read_text(encoding="utf-8")

    def releases_block(self) -> str:
        source = self.read(ROOT / "views" / "operations.php")
        match = re.search(
            r"<\?php if \(\$moduleKey === 'releases'\): \?>(.*?)<\?php elseif \(\$moduleKey === 'service'\): \?>",
            source,
            re.S,
        )
        self.assertIsNotNone(match, "the releases branch must remain a bounded view block")
        return match.group(1)

    def test_approved_page_structure_is_present_without_the_old_redundant_summary(self) -> None:
        block = self.releases_block()
        for marker in (
            "release-management-page",
            "r6-top-grid",
            "r6-link-panel",
            "r6-upload-intro",
            "r6-editor",
            "r6-review",
            "r6-release-list",
        ):
            self.assertIn(marker, block)
        self.assertNotIn("cc_page_summary", block)
        self.assertNotIn("release-current", block)
        self.assertNotIn("record-card", block)

    def test_official_link_and_downloader_contract_are_exact(self) -> None:
        block = self.releases_block()
        self.assertIn("رابط التحميل الرسمي", block)
        self.assertIn("$shortUrl = 'https://hulksa.com/app/'", block)
        self.assertIn('data-copy-value="<?= cc_e($shortUrl) ?>"', block)
        self.assertIn("hulksa.com/app/", block)
        self.assertIn("Downloader", block)
        self.assertIn("5672332", block)
        self.assertIn("الرابط والكود ثابتان مع كل تحديث", block)
        self.assertIn("النسخة المنشورة", block)
        self.assertIn("إجباري", block)
        self.assertIn("اختياري", block)
        self.assertIn("لا يوجد إصدار منشور", block)

    def test_upload_and_review_flow_use_real_authoritative_actions(self) -> None:
        block = self.releases_block()
        for action in (
            "upload_release",
            "activate_release",
            "update_release_policy",
            "disable_release",
            "delete_release",
        ):
            self.assertIn(f'name="action" value="{action}"', block)
        self.assertIn('name="csrf_token"', block)
        self.assertIn('name="apk"', block)
        self.assertIn('enctype="multipart/form-data"', block)
        self.assertIn("مراجعة التحديث", block)
        self.assertIn("نشر على الرابط المختصر", block)
        self.assertIn("حفظ دون نشر", block)
        self.assertIn("تعديل السياسة قبل النشر", block)
        self.assertIn("data-r6-min-confirm", block)
        self.assertIn("data-r6-publish", block)
        self.assertIn("data-confirm=", block)

    def test_uploads_do_not_filter_apk_files_or_create_demo_records(self) -> None:
        block = self.releases_block()
        self.assertNotIn("accept=", block)
        for mock_text in ("نموذج للمراجعة", "محاكاة", "ملف تجريبي", "معاينة تفاعلية فقط"):
            self.assertNotIn(mock_text, block)
        self.assertNotIn("lucide", block)
        self.assertNotIn("fonts.googleapis", block)

    def test_release_rows_are_compact_secondary_and_escape_owner_data(self) -> None:
        block = self.releases_block()
        self.assertIn("<details", block)
        self.assertIn("<summary", block)
        self.assertIn("r6-release-row", block)
        self.assertIn("r6-release-details", block)
        self.assertIn("destructive-zone", block)
        self.assertIn("button--danger", block)
        self.assertIn('data-confirm="حذف هذا الإصدار نهائيًا', block)
        self.assertIn("cc_e($release['version_name'])", block)
        self.assertIn("cc_e($release['release_notes'])", block)
        self.assertNotIn("apk_path", block)
        self.assertNotIn("apk_sha256", block)

    def test_copy_failure_has_a_selectable_fallback_and_no_false_success(self) -> None:
        block = self.releases_block()
        self.assertIn("data-copy-fallback", block)
        self.assertIn("data-copy-status", block)
        self.assertIn("readonly hidden", block)
        javascript = self.read(ROOT / "assets" / "app.js")
        self.assertIn("navigator.clipboard.writeText(value)", javascript)
        self.assertIn("fallback.hidden = false", javascript)
        self.assertIn("الرابط محدد وجاهز للنسخ اليدوي", javascript)
        self.assertNotIn("catch (_error) {\n                setStatus('تم نسخ الرابط.'", javascript)

    def test_page_scoped_styles_and_asset_cache_keys(self) -> None:
        css = self.read(ROOT / "assets" / "app.css")
        self.assertIn(".release-management-page", css)
        self.assertIn("--r6-gold: #e6c352", css)
        self.assertIn("@media (max-width: 760px)", css)
        self.assertNotIn("fonts.googleapis.com", css)
        layout = self.read(ROOT / "views" / "layout.php")
        login = self.read(ROOT / "login.php")
        self.assertIn("app.css?v=6.2.0", layout)
        self.assertIn("app.js?v=6.1.0", layout)
        self.assertIn("app.css?v=6.2.0", login)

    def test_adapter_binds_exact_staged_release_and_safe_view_facts(self) -> None:
        adapter = self.read(ROOT / "lib" / "operations-adapter.php")
        self.assertIn("cc_operations_release_rows", adapter)
        self.assertIn("cc_operations_remember_review_release_id", adapter)
        self.assertIn("WHERE version_code = :version_code LIMIT 1", adapter)
        self.assertIn("ops_release_public_apk_url($release)", adapter)
        self.assertIn("ops_release_artifact_is_readable($release)", adapter)
        self.assertIn("'file_name'", adapter)
        self.assertIn("'sha256'", adapter)
        self.assertIn("'public_apk_url'", adapter)
        self.assertNotIn("'apk_path' =>", adapter)
        self.assertNotIn("'apk_sha256' =>", adapter)

    def test_shared_release_helpers_fail_closed_and_feed_the_update_api(self) -> None:
        operations = self.read(OPS / "lib" / "operations.php")
        self.assertIn("function ops_release_relative_apk_path", operations)
        self.assertIn("function ops_release_public_apk_url", operations)
        self.assertIn("function ops_release_artifact_is_readable", operations)
        self.assertIn("str_contains($path, '..')", operations)
        self.assertIn("str_contains($path, '%')", operations)
        self.assertIn("releases/[A-Za-z0-9]", operations)
        self.assertIn("ops_release_public_apk_url($release)", operations)
        self.assertNotIn("ops_public_url((string) $release['apk_path'])", operations)

    def test_permanent_resolver_is_read_only_and_uses_the_same_authority(self) -> None:
        source = self.read(APP)
        self.assertIn("ops_active_release(ops_db())", source)
        self.assertIn("ops_release_public_apk_url($release)", source)
        self.assertIn("ops_release_artifact_is_readable($release)", source)
        self.assertIn("Cache-Control: no-store, no-cache, must-revalidate, max-age=0", source)
        self.assertIn("header('Location: ' . $destination, true, 302)", source)
        self.assertIn("['GET', 'HEAD']", source)
        self.assertIn("405", source)
        self.assertIn("404", source)
        self.assertIn("503", source)
        self.assertNotIn("$_GET", source)
        self.assertNotIn("$_POST", source)
        self.assertNotIn("control-center", source)
        self.assertNotIn("hulk-sa-v1.0.0-68", source)
        self.assertNotIn("file_get_contents", source)
        self.assertNotIn("curl_", source)

    def test_route_description_matches_the_approved_supporting_line(self) -> None:
        routes = self.read(ROOT / "lib" / "routes.php")
        self.assertIn("'description' => 'ارفع النسخة، راجعها، وانشرها على رابطك الثابت.'", routes)

    def test_release_mutations_remain_post_only_and_csrf_protected(self) -> None:
        index = self.read(ROOT / "index.php")
        self.assertIn("($_SERVER['REQUEST_METHOD'] ?? 'GET') === 'POST'", index)
        csrf = index.find("cc_require_csrf()")
        dispatch = index.find("cc_operations_handle_post(")
        self.assertGreaterEqual(csrf, 0)
        self.assertGreaterEqual(dispatch, 0)
        self.assertLess(csrf, dispatch, "CSRF must run before release mutation dispatch")
        block = self.releases_block()
        self.assertEqual(block.count('method="post"'), block.count("<form"))
        self.assertIn("activate_release", block)
        self.assertNotIn("REQUEST_METHOD", block)


if __name__ == "__main__":
    unittest.main()
