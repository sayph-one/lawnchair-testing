package app.lawnchair.ui

import app.lawnchair.util.AllowedApps
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Smoke tests for registration overlay behavior.
 * Validates the core logic paths without requiring an Android device.
 */
class RegistrationOverlayManagerTest {

    @Test
    fun `allowed apps are visible when registered`() {
        // Simulate registered state
        AllowedApps.setCachedRegistrationStatus(true)

        assertTrue(AllowedApps.isAllowedCached("com.sayph.phone"))
        assertTrue(AllowedApps.isAllowedCached("com.sayph.cam"))
        assertTrue(AllowedApps.isAllowedCached("one.sayph.weather"))
    }

    @Test
    fun `all apps blocked when not registered`() {
        AllowedApps.setCachedRegistrationStatus(false)

        assertFalse(AllowedApps.isAllowedCached("com.sayph.phone"))
        assertFalse(AllowedApps.isAllowedCached("com.sayph.cam"))
        assertFalse(AllowedApps.isAllowedCached("one.sayph.weather"))
    }

    @Test
    fun `non-allowed apps blocked even when registered`() {
        AllowedApps.setCachedRegistrationStatus(true)

        assertFalse(AllowedApps.isAllowedCached("com.facebook.katana"))
        assertFalse(AllowedApps.isAllowedCached("com.android.settings"))
    }

    @Test
    fun `sayph agent always hidden regardless of registration`() {
        AllowedApps.setCachedRegistrationStatus(true)
        assertFalse(AllowedApps.isAllowedCached("com.sayph.sayphagent"))

        AllowedApps.setCachedRegistrationStatus(false)
        assertFalse(AllowedApps.isAllowedCached("com.sayph.sayphagent"))
    }

    @Test
    fun `registration state change toggles app visibility`() {
        AllowedApps.setCachedRegistrationStatus(true)
        assertTrue(AllowedApps.isAllowedCached("com.sayph.phone"))

        AllowedApps.setCachedRegistrationStatus(false)
        assertFalse(AllowedApps.isAllowedCached("com.sayph.phone"))

        AllowedApps.setCachedRegistrationStatus(true)
        assertTrue(AllowedApps.isAllowedCached("com.sayph.phone"))
    }
}
