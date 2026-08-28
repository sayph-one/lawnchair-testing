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
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.dialer"))
        assertTrue(AllowedApps.isInAllowedList("com.simplemobiletools.smsmessenger"))
        assertTrue(AllowedApps.isInAllowedList("com.sayph.notes"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.settings"))
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
        assertTrue(AllowedApps.isInAllowedList("com.sayph.notes.debug"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.settings.debug"))
        assertTrue(AllowedApps.isInAllowedList("app.organicmaps.debug"))
    }

    @Test
    fun `retired fossify notes app is no longer allowed`() {
        assertFalse(AllowedApps.isInAllowedList("org.fossify.notes"))
        assertFalse(AllowedApps.isInAllowedList("org.fossify.notes.debug"))
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
        assertTrue(packages.contains("com.simplemobiletools.dialer"))
        assertTrue(packages.contains("com.sec.android.daemonapp"))
        assertTrue(packages.size >= 10)
    }
}
