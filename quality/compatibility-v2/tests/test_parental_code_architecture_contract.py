import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[3]
DATA_ROOT = REPO_ROOT / "app/src/main/java/sa/hulksa/player/data"
UI_ROOT = REPO_ROOT / "app/src/main/java/sa/hulksa/player/ui"


class ParentalCodeArchitectureContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.parent_store = (DATA_ROOT / "ParentalCodeCredentialStore.kt").read_text()
        cls.profile_store = (DATA_ROOT / "ProfilePinCredentialStore.kt").read_text()
        cls.app = (UI_ROOT / "ProfileAwareHulkApp.kt").read_text()
        cls.parent_ui = (
            UI_ROOT / "screens/ParentalCodeSecurityScreen.kt"
        ).read_text()
        cls.switch_policy = (UI_ROOT / "ProfilePickerPolicy.kt").read_text()

    def test_parental_credential_has_its_own_account_scoped_namespace(self):
        self.assertIn("class ParentalCodeCredentialStore", self.parent_store)
        self.assertIn(
            'PREFERENCES_NAME = "hulk_parental_code_credentials_v1"',
            self.parent_store,
        )
        self.assertIn("accountScopedPreferencesName(PREFERENCES_NAME, accountId)", self.parent_store)
        self.assertNotIn('"profile:$profileId', self.parent_store)
        self.assertNotIn('"hulk_profile_pin_credentials_v1"', self.parent_store)

    def test_parental_code_uses_salted_verification_off_the_main_dispatcher(self):
        self.assertIn("SecureRandom()", self.parent_store)
        self.assertIn("deriveParentalCodeVerifier", self.parent_store)
        self.assertIn("MessageDigest.isEqual", self.parent_store)
        self.assertIn("Dispatchers.Default", self.parent_store)
        self.assertIn("Dispatchers.IO", self.parent_store)
        self.assertGreaterEqual(self.parent_store.count("withContext(cpuDispatcher)"), 2)
        self.assertGreaterEqual(self.parent_store.count("withContext(ioDispatcher)"), 4)
        self.assertIn("putString(KEY_VERIFIER", self.parent_store)
        self.assertNotRegex(self.parent_store, r"putString\([^\n]*(?:code|pin)\b")

    def test_app_never_stores_parental_code_as_an_adult_profile_pin(self):
        self.assertIn("ParentalCodeCredentialStore(context)", self.app)
        self.assertIn("parentalCodeCredentialStore.setCode(code)", self.app)
        self.assertIn("parentalCodeCredentialStore::verifyCode", self.app)
        self.assertNotIn("primaryParentCredentialProfile", self.app)
        self.assertNotRegex(
            self.app,
            r"profilePinCredentialStore\.setPin\([^\n]*(?:primary|Adult)",
        )
        protected_ids = re.search(
            r"val protectedProfileIds = remember\(profiles, pinRevision\) \{(?P<body>.*?)\n    \}",
            self.app,
            re.DOTALL,
        )
        self.assertIsNotNone(protected_ids)
        self.assertIn("profilePinCredentialStore.hasPin", protected_ids.group("body"))
        self.assertNotIn("parentalCode", protected_ids.group("body"))

    def test_parental_ui_contains_no_profile_identity_or_profile_pin_copy(self):
        self.assertNotIn("UserProfile", self.parent_ui)
        self.assertNotIn("ProfilePinProtectionScreen", self.parent_ui)
        self.assertNotIn("ProfilePinUnlockScreen", self.parent_ui)
        self.assertIn('"إنشاء رمز الوالدين"', self.parent_ui)
        self.assertIn('"تأكيد رمز الوالدين"', self.parent_ui)
        self.assertIn('"رمز الوالدين غير صحيح"', self.parent_ui)
        self.assertIn(
            '"سيُستخدم رمز الوالدين للخروج من وضع الأطفال وإدارة الملفات الشخصية."',
            self.parent_ui,
        )
        for forbidden_copy in (
            "حماية الملف برمز PIN",
            "رمز PIN للملف",
            "حماية الملف الشخصي",
        ):
            self.assertNotIn(forbidden_copy, self.parent_ui)

    def test_kids_exit_checks_parental_code_before_an_optional_target_pin(self):
        parental_check = self.switch_policy.index("!parentalAuthorizationGranted")
        target_pin_check = self.switch_policy.index("if (targetProtected")
        self.assertLess(parental_check, target_pin_check)
        self.assertIn("ProfileSwitchAuthorization.REQUIRE_PARENTAL_CODE", self.switch_policy)
        self.assertIn("ParentalCodeAuthorizationAction.OpenManagement", self.app)
        self.assertIn("parentalAuthorizationGranted = true", self.app)

    def test_profile_pin_management_remains_independent_when_kids_exist(self):
        self.assertIn("profilePinCredentialStore.clearPin(securityProfile.id)", self.app)
        self.assertNotIn("canClearPrimaryParentPin", self.app)
        self.assertNotIn("لا يمكن إزالة رمز الوالدين ما دامت هناك ملفات أطفال", self.app)
        manage_pin = re.search(
            r"onManagePin = \{ profile ->(?P<body>.*?)\n            \},",
            self.app,
            re.DOTALL,
        )
        self.assertIsNotNone(manage_pin)
        self.assertIn("pinSecurityProfileId = profile.id", manage_pin.group("body"))
        self.assertNotIn("parentalCode", manage_pin.group("body"))

    def test_legacy_migration_copies_but_never_clears_the_profile_pin(self):
        self.assertIn("COPY_EXISTING_PROFILE_PIN", self.parent_store)
        self.assertIn("legacy_profile_pin_migration_complete_v1", self.parent_store)
        self.assertIn("credentialSnapshotForMigration", self.parent_store)
        self.assertNotIn("clearPin(", self.parent_store)
        self.assertNotIn("clearCredential(", self.parent_store)
        self.assertIn("salt = source.salt.copyOf()", self.parent_store)
        self.assertIn("verifier = source.verifier.copyOf()", self.parent_store)

    def test_raw_parental_digits_are_not_saved_in_compose_state(self):
        self.assertIn("var firstCode by remember(credentialScopeKey)", self.parent_ui)
        self.assertNotIn("var firstCode by rememberSaveable", self.parent_ui)
        pin_screen = (UI_ROOT / "screens/ProfilePinSecurityScreen.kt").read_text()
        self.assertIn("var pin by remember(stateKey, title, resetToken)", pin_screen)
        self.assertNotIn("var pin by rememberSaveable", pin_screen)

    def test_parental_authorization_cannot_survive_process_recreation(self):
        self.assertIn(
            "var managingProfiles by remember(activeAccountId) { mutableStateOf(false) }",
            self.app,
        )
        self.assertIn("var pinUnlockTargetId by remember(activeAccountId) {", self.app)
        self.assertIn(
            "var parentalCodeAuthorizationAction by remember(activeAccountId) {",
            self.app,
        )
        self.assertNotIn("var managingProfiles by rememberSaveable", self.app)
        self.assertNotIn("var pinUnlockTargetId by rememberSaveable", self.app)


if __name__ == "__main__":
    unittest.main()
