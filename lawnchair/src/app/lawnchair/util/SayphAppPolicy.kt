package app.lawnchair.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.Cursor
import android.net.Uri
import android.util.Log
import androidx.annotation.VisibleForTesting

/**
 * Hides apps that a parent has turned off via the SayphAgent portal.
 *
 * SayphAgent exposes per-app "policy" through its ContentProvider
 * (`com.sayph.sayphagent.provider`) — e.g. `music_policy` and `camera_policy`. This object reads
 * those policies, caches which packages are currently disabled, and lets [AllowedApps] hide them
 * from every launcher surface (workspace load, drawer, auto-placement) through the single
 * `isInAllowedList` / `isAllowed` choke-point.
 *
 * To gate a new app, add one [PolicyGroup] to [groups] — no other launcher change is required.
 *
 * Closing an app that is already open is *not* handled here; that remains SayphAgent's
 * responsibility. This object only controls icon visibility.
 */
object SayphAppPolicy {
    private const val TAG = "SayphAppPolicy"
    private const val AUTHORITY = "com.sayph.sayphagent.provider"

    /**
     * One gated app group.
     *
     * @param key human-readable id, for logging.
     * @param packages base package names hidden when this group is disabled (matched against
     *   sub-packages too, so `.debug` / `.beta` variants are covered).
     * @param providerPath the ContentProvider path to read (e.g. `music_policy`).
     * @param broadcastAction the action SayphAgent broadcasts when this policy changes, or `null`
     *   when the agent sends none (then changes apply on the launcher's next resume refresh).
     * @param isDisabled reads the policy's first cursor row and returns true when the group is off.
     */
    data class PolicyGroup(
        val key: String,
        val packages: Set<String>,
        val providerPath: String,
        val broadcastAction: String?,
        val isDisabled: (Cursor) -> Boolean,
    )

    /**
     * The registry of gated apps. Add an entry here to gate a new app.
     *
     * Policies fail-safe to *enabled* (app visible) when absent or unreadable, matching the agent's
     * own evaluators — so an old agent or a query error never hides an app unexpectedly.
     */
    val groups = listOf(
        // Music: a single master on/off. enabled=0 => hide the music app.
        PolicyGroup(
            key = "music",
            packages = setOf("one.sayph.music"),
            providerPath = "music_policy",
            broadcastAction = "com.sayph.MUSIC_SETTINGS_CHANGED",
            isDisabled = { c -> c.getInt(c.getColumnIndexOrThrow("enabled")) == 0 },
        ),
        // Camera: disabled only when BOTH lenses are off. The agent sends no camera broadcast yet,
        // so changes are picked up on the launcher's next onResume refresh. Camera apps only —
        // gallery stays available so existing photos can still be viewed.
        PolicyGroup(
            key = "camera",
            packages = setOf(
                "com.sayph.cam",
                "com.sec.android.app.camera",
                "com.sec.factory.camera",
            ),
            providerPath = "camera_policy",
            broadcastAction = null,
            isDisabled = { c ->
                c.getInt(c.getColumnIndexOrThrow("front_enabled")) == 0 &&
                    c.getInt(c.getColumnIndexOrThrow("back_enabled")) == 0
            },
        ),
    )

    @Volatile
    private var disabledPackages: Set<String> = emptySet()

    /**
     * Re-read every policy from the agent and rebuild the disabled-packages cache. Safe to call off
     * the main thread; queries are cheap ContentProvider reads. A failure for one group is logged
     * and treated as enabled, so it can't blank out the others.
     */
    fun refresh(context: Context) {
        val disabled = mutableSetOf<String>()
        for (group in groups) {
            try {
                val uri = Uri.parse("content://$AUTHORITY/${group.providerPath}")
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst() && group.isDisabled(cursor)) {
                        disabled.addAll(group.packages)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read ${group.providerPath}; treating '${group.key}' as enabled", e)
            }
        }
        disabledPackages = disabled
        Log.d(TAG, "Refreshed app policy. Disabled packages: $disabled")
    }

    /**
     * True when [packageName] belongs to a currently-disabled group. Context-free — reads the cache
     * populated by [refresh] — so the existing context-free filter call sites need no changes.
     * Uses the same base/sub-package matching as [AllowedApps.isInAllowedList].
     */
    fun isDisabled(packageName: String): Boolean {
        if (packageName.isEmpty()) return false
        return disabledPackages.any { base ->
            packageName == base ||
                (
                    packageName.startsWith("$base.") &&
                        packageName.substring(base.length + 1).matches(Regex("^[a-zA-Z0-9._-]+$"))
                    )
        }
    }

    /** Broadcast actions to listen for (groups whose agent pushes change notifications). */
    fun broadcastActions(): List<String> = groups.mapNotNull { it.broadcastAction }

    /**
     * Register a receiver for every policy-change broadcast. On any such broadcast the cache is
     * refreshed and [onChanged] is invoked (typically to reload the workspace). Returns the
     * receiver so the caller can unregister it; returns null when no group broadcasts.
     *
     * Registered EXPORTED on API 33+ because the broadcast originates from the agent's package.
     */
    fun registerReceiver(context: Context, onChanged: () -> Unit): BroadcastReceiver? {
        val actions = broadcastActions()
        if (actions.isEmpty()) return null

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.action in actions) {
                    Log.d(TAG, "Received policy change broadcast: ${intent.action}")
                    refresh(receiverContext)
                    onChanged()
                }
            }
        }
        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        Log.d(TAG, "App policy receiver registered for: $actions")
        return receiver
    }

    /** Seed the disabled-packages cache directly. For unit tests only. */
    @VisibleForTesting
    fun setDisabledPackagesForTesting(packages: Set<String>) {
        disabledPackages = packages
    }
}
