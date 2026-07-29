from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[3]
GRADLE = ROOT / "app/build.gradle.kts"
COMPOSE_TEST = (
    ROOT
    / "app/src/androidTest/java/sa/hulksa/player/ui/MainShellComposeQualityTest.kt"
)
DOWNLOAD_TEST = (
    ROOT
    / "app/src/androidTest/java/sa/hulksa/player/data/DownloadRepositoryFixtureTest.kt"
)
SMOKE_TEST = (
    ROOT
    / "app/src/androidTest/java/sa/hulksa/player/CompatibilitySmokeTest.kt"
)


class AndroidTestContractTest(unittest.TestCase):
    def test_orchestrator_clears_state_between_instrumentation_tests(self) -> None:
        source = GRADLE.read_text(encoding="utf-8")
        self.assertIn('execution = "ANDROIDX_TEST_ORCHESTRATOR"', source)
        self.assertIn('testInstrumentationRunnerArguments["clearPackageData"] = "true"', source)
        self.assertIn('androidx.test:orchestrator:1.6.1', source)

    def test_compose_test_uses_semantics_not_coordinates(self) -> None:
        source = COMPOSE_TEST.read_text(encoding="utf-8")
        self.assertIn("createComposeRule", source)
        self.assertIn("onNodeWithContentDescription", source)
        self.assertIn("fetchSemanticsNode", source)
        self.assertNotIn("import androidx.compose.ui.test.assertExists", source)
        self.assertNotIn("import androidx.compose.ui.test.assertDoesNotExist", source)
        self.assertNotIn("performTouchInput", source)
        self.assertNotIn("click(", source)

    def test_smoke_test_uses_public_activity_state(self) -> None:
        source = SMOKE_TEST.read_text(encoding="utf-8")
        self.assertNotIn("activity.resultCode", source)
        self.assertIn("assertFalse(activity.isFinishing)", source)

    def test_download_test_uses_production_repository_and_loopback_fixture(self) -> None:
        source = DOWNLOAD_TEST.read_text(encoding="utf-8")
        self.assertIn("DownloadRepository(context)", source)
        self.assertIn("MockWebServer", source)
        self.assertIn("bytesDownloaded > 0L", source)
        self.assertIn("integrityVerified", source)
        self.assertNotIn("3162356.xyz", source)
        self.assertNotIn("hulksa.com", source)


if __name__ == "__main__":
    unittest.main()
