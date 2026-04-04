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

        assertTrue(AllowedApps.isAllowedCached("com.simplemobiletools.dialer"))
        assertTrue(AllowedApps.isAllowedCached("com.sec.android.app.camera"))
        assertTrue(AllowedApps.isAllowedCached("com.sec.android.daemonapp"))
    }

    @Test
    fun `all apps blocked when not registered`() {
        AllowedApps.setCachedRegistrationStatus(false)

        assertFalse(AllowedApps.isAllowedCached("com.simplemobiletools.dialer"))
        assertFalse(AllowedApps.isAllowedCached("com.sec.android.app.camera"))
        assertFalse(AllowedApps.isAllowedCached("com.sec.android.daemonapp"))
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
        assertTrue(AllowedApps.isAllowedCached("com.simplemobiletools.dialer"))

        AllowedApps.setCachedRegistrationStatus(false)
        assertFalse(AllowedApps.isAllowedCached("com.simplemobiletools.dialer"))

        AllowedApps.setCachedRegistrationStatus(true)
        assertTrue(AllowedApps.isAllowedCached("com.simplemobiletools.dialer"))
    }
}
