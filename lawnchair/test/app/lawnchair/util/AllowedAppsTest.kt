package app.lawnchair.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AllowedAppsTest {

    @Test
    fun `exact match allowed packages are allowed`() {
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.dialer"))
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.smsmessenger"))
        assertTrue(AllowedApps.isInAllowedList("org.fossify.notes"))
        assertTrue(AllowedApps.isInAllowedList("app.organicmaps"))
        assertTrue(AllowedApps.isInAllowedList("com.sec.android.gallery3d"))
        assertTrue(AllowedApps.isInAllowedList("com.sec.android.app.camera"))
        assertTrue(AllowedApps.isInAllowedList("com.sec.android.app.clockpackage"))
        assertTrue(AllowedApps.isInAllowedList("com.sec.android.app.popupcalculator"))
        assertTrue(AllowedApps.isInAllowedList("com.sec.android.daemonapp"))
    }

    @Test
    fun `debug variants of allowed packages are allowed`() {
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.dialer.debug"))
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.smsmessenger.debug"))
        assertTrue(AllowedApps.isInAllowedList("org.fossify.notes.debug"))
        assertTrue(AllowedApps.isInAllowedList("app.organicmaps.debug"))
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
        assertFalse(AllowedApps.isInAllowedList("com.simplemobiletools.dialermalware"))
        assertFalse(AllowedApps.isInAllowedList("app.organicmapsfake"))
    }

    @Test
    fun `sub-packages of allowed apps are allowed`() {
        // Legitimate sub-packages (e.g. debug, beta variants)
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.dialer.beta"))
        assertTrue(AllowedApps.isInAllowedList("app.organicmaps.staging"))
    }

    @Test
    fun `empty and blank package names are not allowed`() {
        assertFalse(AllowedApps.isInAllowedList(""))
    }

    @Test
    fun `getAllowedPackages returns all base packages`() {
        val packages = AllowedApps.getAllowedPackages()
        assertTrue(packages.contains("com.simplemobiletools.dialer"))
        assertTrue(packages.contains("com.sec.android.daemonapp"))
        assertTrue(packages.size >= 10)
    }
}
