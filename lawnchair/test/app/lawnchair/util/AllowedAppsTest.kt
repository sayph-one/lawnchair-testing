package app.lawnchair.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedAppsTest {

    @After
    fun resetPolicy() {
        // Other tests in this class assume nothing is disabled by policy.
        SayphAppPolicy.setDisabledPackagesForTesting(emptySet())
    }

    @Test
    fun `exact match allowed packages are allowed`() {
        assertTrue(AllowedApps.isInAllowedList("com.sayph.phone"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.messenger"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.notes"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.cam"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.settings"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.music"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.clock"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.calculator"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.weather"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.gallery"))
    }

    @Test
    fun `debug variants of allowed packages are allowed`() {
        assertTrue(AllowedApps.isInAllowedList("com.sayph.phone.debug"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.messenger.debug"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.notes.debug"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.settings.debug"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.gallery.debug"))
    }

    @Test
    fun `retired fossify notes app is no longer allowed`() {
        assertFalse(AllowedApps.isInAllowedList("org.fossify.notes"))
        assertFalse(AllowedApps.isInAllowedList("org.fossify.notes.debug"))
    }

    @Test
    fun `retired simple mobile tools apps are no longer allowed`() {
        assertFalse(AllowedApps.isInAllowedList("com.simplemobiletools.dialer"))
        assertFalse(AllowedApps.isInAllowedList("com.simplemobiletools.dialer.debug"))
        assertFalse(AllowedApps.isInAllowedList("com.simplemobiletools.smsmessenger"))
        assertFalse(AllowedApps.isInAllowedList("com.simplemobiletools.smsmessenger.debug"))
    }

    @Test
    fun `retired organic maps app is no longer allowed`() {
        assertFalse(AllowedApps.isInAllowedList("app.organicmaps"))
        assertFalse(AllowedApps.isInAllowedList("app.organicmaps.debug"))
    }

    @Test
    fun `retired samsung stock apps are no longer allowed`() {
        assertFalse(AllowedApps.isInAllowedList("com.sec.android.gallery3d"))
        assertFalse(AllowedApps.isInAllowedList("com.sec.android.app.camera"))
        assertFalse(AllowedApps.isInAllowedList("com.sec.android.app.clockpackage"))
        assertFalse(AllowedApps.isInAllowedList("com.sec.android.app.clockpackage.ClockPackage"))
        assertFalse(AllowedApps.isInAllowedList("com.sec.android.app.popupcalculator"))
    }

    @Test
    fun `sayph agent is never allowed in app list`() {
        assertFalse(AllowedApps.isInAllowedList("com.sayph.sayphagent"))
    }

    @Test
    fun `random packages are not allowed`() {
        assertFalse(AllowedApps.isInAllowedList("com.facebook.katana"))
        assertFalse(AllowedApps.isInAllowedList("com.instagram.android"))
        assertFalse(AllowedApps.isInAllowedList("com.google.android.youtube"))
        assertFalse(AllowedApps.isInAllowedList("com.android.chrome"))
        assertFalse(AllowedApps.isInAllowedList("com.android.settings"))
    }

    @Test
    fun `package prefix attacks are blocked`() {
        // A malicious package with an allowed prefix but different app
        assertFalse(AllowedApps.isInAllowedList("com.sayph.phonemalware"))
        assertFalse(AllowedApps.isInAllowedList("one.sayph.settingsfake"))
    }

    @Test
    fun `sub-packages of allowed apps are allowed`() {
        // Legitimate sub-packages (e.g. debug, beta variants)
        assertTrue(AllowedApps.isInAllowedList("com.sayph.phone.beta"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.settings.staging"))
    }

    @Test
    fun `empty and blank package names are not allowed`() {
        assertFalse(AllowedApps.isInAllowedList(""))
    }

    @Test
    fun `policy-disabled app is removed from the allowed list`() {
        // Camera app is in the allowed list by default...
        assertTrue(AllowedApps.isInAllowedList("com.sayph.cam"))
        // ...but once the agent disables the camera, it must be hidden.
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("com.sayph.cam"))
        assertFalse(AllowedApps.isInAllowedList("com.sayph.cam"))
        assertFalse(AllowedApps.isInAllowedList("com.sayph.cam.debug"))
    }

    @Test
    fun `disabling one app does not hide other allowed apps`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertFalse(AllowedApps.isInAllowedList("one.sayph.music"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.cam"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.notes"))
    }

    @Test
    fun `getAllowedPackages returns all base packages`() {
        val packages = AllowedApps.getAllowedPackages()
        assertTrue(packages.contains("com.sayph.phone"))
        assertTrue(packages.contains("one.sayph.settings"))
        assertTrue(packages.size >= 10)
    }
}
