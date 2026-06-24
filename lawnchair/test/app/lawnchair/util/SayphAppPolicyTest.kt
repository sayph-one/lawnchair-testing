package app.lawnchair.util

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SayphAppPolicyTest {

    @After
    fun tearDown() {
        // Reset to the fail-open default so tests don't leak state into each other
        // or into AllowedAppsTest.
        SayphAppPolicy.setDisabledPackagesForTesting(emptySet())
    }

    @Test
    fun `no policy means nothing is disabled`() {
        assertFalse(SayphAppPolicy.isDisabled("one.sayph.music"))
        assertFalse(SayphAppPolicy.isDisabled("com.sayph.cam"))
    }

    @Test
    fun `disabled base package is reported disabled`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertTrue(SayphAppPolicy.isDisabled("one.sayph.music"))
    }

    @Test
    fun `disabled package matches debug and sub-package variants`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertTrue(SayphAppPolicy.isDisabled("one.sayph.music.debug"))
        assertTrue(SayphAppPolicy.isDisabled("one.sayph.music.beta"))
    }

    @Test
    fun `prefix attacks are not treated as disabled`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertFalse(SayphAppPolicy.isDisabled("one.sayph.musicplayer"))
    }

    @Test
    fun `empty package is not disabled`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertFalse(SayphAppPolicy.isDisabled(""))
    }

    @Test
    fun `unrelated packages are not disabled`() {
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.music"))
        assertFalse(SayphAppPolicy.isDisabled("com.sayph.cam"))
        assertFalse(SayphAppPolicy.isDisabled("org.fossify.notes"))
    }

    @Test
    fun `every camera package is gated by the camera group`() {
        // When the camera is disabled, refresh() adds all camera packages to the set.
        SayphAppPolicy.setDisabledPackagesForTesting(
            setOf("com.sayph.cam", "com.sec.android.app.camera", "com.sec.factory.camera"),
        )
        assertTrue(SayphAppPolicy.isDisabled("com.sayph.cam"))
        assertTrue(SayphAppPolicy.isDisabled("com.sec.android.app.camera"))
        assertTrue(SayphAppPolicy.isDisabled("com.sec.factory.camera"))
        // Gallery is intentionally NOT part of the camera group.
        assertFalse(SayphAppPolicy.isDisabled("one.sayph.gallery"))
        assertFalse(SayphAppPolicy.isDisabled("com.sec.android.gallery3d"))
    }

    @Test
    fun `registry exposes broadcast actions only for groups that have one`() {
        val actions = SayphAppPolicy.broadcastActions()
        assertTrue(actions.contains("com.sayph.MUSIC_SETTINGS_CHANGED"))
        // Camera has no agent broadcast yet, so it must not appear.
        assertFalse(actions.contains("com.sayph.CAMERA_SETTINGS_CHANGED"))
    }
}
