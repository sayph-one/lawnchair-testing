package app.lawnchair.util

import org.junit.After
import org.junit.Assert.assertEquals
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
        assertFalse(SayphAppPolicy.isDisabled("com.sayph.notes"))
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

    private fun group(key: String) = SayphAppPolicy.groups.first { it.key == key }

    @Test
    fun `games and launchpad are hidden unless the agent says they are on`() {
        for (key in listOf("games", "launchpad")) {
            val g = group(key)
            assertTrue("$key should be off by default", g.hiddenUnlessEnabled)
            // No answer at all (old agent without the path, no row, failed read): hidden.
            assertTrue(SayphAppPolicy.groupDisabled(g, null))
            // The agent's own verdict wins either way.
            assertTrue(SayphAppPolicy.groupDisabled(g, true))
            assertFalse(SayphAppPolicy.groupDisabled(g, false))
        }
    }

    @Test
    fun `music and camera still fail open when the agent gives no answer`() {
        assertFalse(SayphAppPolicy.groupDisabled(group("music"), null))
        assertFalse(SayphAppPolicy.groupDisabled(group("camera"), null))
    }

    @Test
    fun `games and launchpad gate the right packages`() {
        assertEquals(setOf("one.sayph.games"), group("games").packages)
        assertEquals(setOf("one.sayph.launchpad"), group("launchpad").packages)
        assertEquals("games_policy", group("games").providerPath)
        assertEquals("launchpad_policy", group("launchpad").providerPath)
        SayphAppPolicy.setDisabledPackagesForTesting(setOf("one.sayph.games", "one.sayph.launchpad"))
        assertTrue(SayphAppPolicy.isDisabled("one.sayph.games.debug"))
        assertTrue(SayphAppPolicy.isDisabled("one.sayph.launchpad"))
        assertFalse(AllowedApps.isInAllowedList("one.sayph.launchpad"))
        SayphAppPolicy.setDisabledPackagesForTesting(emptySet())
        assertTrue(AllowedApps.isInAllowedList("one.sayph.launchpad"))
        assertTrue(AllowedApps.isInAllowedList("one.sayph.games"))
    }

    @Test
    fun `games and launchpad listen for the agent's broadcasts`() {
        val actions = SayphAppPolicy.broadcastActions()
        assertTrue(actions.contains("com.sayph.GAMES_SETTINGS_CHANGED"))
        assertTrue(actions.contains("com.sayph.LAUNCHPAD_SETTINGS_CHANGED"))
    }
}
