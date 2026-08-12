package sa.hulksa.player.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsServerCatalogPolicyTest {
    @Test
    fun explicitKidsCategoriesAreAcceptedAcrossArabicAndEnglish() {
        assertTrue(isExplicitKidsCategoryName("Kids"))
        assertTrue(isExplicitKidsCategoryName("KIDS MOVIES | AR"))
        assertTrue(isExplicitKidsCategoryName("Children Series"))
        assertTrue(isExplicitKidsCategoryName("أفلام أطفال"))
        assertTrue(isExplicitKidsCategoryName("مسلسلات للأطفال"))
        assertTrue(isExplicitKidsCategoryName("قنوات الصغار"))
    }

    @Test
    fun broadOrAmbiguousCategoriesAreRejectedFailClosed() {
        assertFalse(isExplicitKidsCategoryName("Animation"))
        assertFalse(isExplicitKidsCategoryName("Cartoon"))
        assertFalse(isExplicitKidsCategoryName("Family"))
        assertFalse(isExplicitKidsCategoryName("Anime"))
        assertFalse(isExplicitKidsCategoryName("General Entertainment"))
        assertFalse(isExplicitKidsCategoryName(""))
    }

    @Test
    fun serverCategoryFilterMustEchoRequestedCategoryForEveryItem() {
        assertTrue(isServerCategoryScopeVerified(listOf("17", "17", "17"), "17"))
        assertTrue(isServerCategoryScopeVerified(emptyList(), "17"))
        assertFalse(isServerCategoryScopeVerified(listOf("17", "99"), "17"))
        assertFalse(isServerCategoryScopeVerified(listOf(""), "17"))
        assertFalse(isServerCategoryScopeVerified(listOf("17"), ""))
    }
}
