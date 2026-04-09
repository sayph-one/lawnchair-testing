package app.lawnchair.workspace

import android.content.Context
import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.util.Log
import android.util.Pair
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.categorizeAppsWithSystemAndGoogle
import app.lawnchair.deck.AddFoldersWithItemsTask
import com.android.launcher3.LauncherModel
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo

/**
 * Single authority for adding allowed apps to the workspace.
 *
 * All paths that need to add apps to the home screen should go through this class.
 * It always uses [AllowedApps.isInAllowedList] (never registration-gated) because
 * registration is a UX concern handled by the overlay. AOSP's [AddWorkspaceItemsTask]
 * handles space-finding, duplicate detection (including inside folders), and DB writes.
 */
class AllowedAppsWorkspaceManager(private val context: Context) {

    /**
     * Returns installed allowed apps that are NOT yet on the workspace.
     * Checks both desktop-level items and items inside folders via [BgDataModel.itemsIdMap].
     * Uses [allApps] (if provided) to get icon-loaded AppInfo objects.
     */
    fun findMissingAllowedApps(dataModel: BgDataModel, allApps: AllAppsList? = null): List<AppInfo> {
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return emptyList()
        val user = Process.myUserHandle()

        val existingPackages = collectWorkspacePackages(dataModel, user)

        // Build index of already-loaded apps (with icons) from AllAppsList
        val loadedApps = mutableMapOf<String, AppInfo>()
        allApps?.data?.forEach { app ->
            app.componentName?.packageName?.let { loadedApps[it] = app }
        }

        val missing = mutableListOf<AppInfo>()
        for (pkg in AllowedApps.getAllowedPackages()) {
            if (!AllowedApps.isInAllowedList(pkg)) continue
            if (existingPackages.contains(pkg)) continue

            // Prefer icon-loaded AppInfo from AllAppsList
            val loaded = loadedApps[pkg]
            if (loaded != null) {
                missing.add(loaded)
            } else {
                // Fallback: create from LauncherApps (icons load on next cache cycle)
                val activities = launcherApps.getActivityList(pkg, user)
                for (activity in activities) {
                    missing.add(AppInfo(context, activity, user))
                }
            }
        }
        return missing.sortedBy { it.title.toString().lowercase() }
    }

    /**
     * Adds missing allowed apps to the workspace as flat icons.
     * Must be called from a [LauncherModel.ModelUpdateTask] (runs on MODEL_EXECUTOR).
     */
    fun ensureMissingAppsOnWorkspace(model: LauncherModel, dataModel: BgDataModel, allApps: AllAppsList? = null) {
        val missing = findMissingAllowedApps(dataModel, allApps)
        if (missing.isEmpty()) {
            Log.d(TAG, "No missing apps — workspace is up to date")
            return
        }

        Log.d(TAG, "Adding ${missing.size} missing apps to workspace")
        val itemList = missing.mapNotNull { appInfo ->
            appInfo.makeWorkspaceItem(context)?.let { Pair.create<ItemInfo, Any?>(it, null) }
        }
        model.addAndBindAddedWorkspaceItems(itemList)
    }

    /**
     * Adds missing allowed apps to the workspace, grouped into Flowerpot categories.
     * Multi-app categories become folders; single-app categories become standalone icons.
     * Enqueues work on MODEL_EXECUTOR so it's safe to call from any thread.
     */
    fun ensureMissingAppsOnWorkspaceCategorized(
        model: LauncherModel,
        onProgress: ((String) -> Unit)? = null,
        onComplete: (() -> Unit)? = null,
    ) {
        model.enqueueModelUpdateTask { taskController, dataModel, apps ->
            val missing = findMissingAllowedApps(dataModel, apps)
            if (missing.isEmpty()) {
                Log.d(TAG, "No missing apps — workspace is up to date")
                onComplete?.invoke()
                return@enqueueModelUpdateTask
            }

            onProgress?.invoke("Categorizing apps...")
            val categorized = categorizeAppsWithSystemAndGoogle(missing, context)

            val foldersToAdd = mutableListOf<FolderInfo>()
            val singleItems = mutableListOf<Pair<ItemInfo, Any?>>()

            categorized.forEach { (category, apps) ->
                if (apps.isEmpty()) return@forEach
                if (apps.size == 1) {
                    apps.first().makeWorkspaceItem(context)?.let {
                        singleItems.add(Pair.create<ItemInfo, Any?>(it, null))
                    }
                } else {
                    val folder = FolderInfo().apply { title = category }
                    apps.forEach { app ->
                        app.makeWorkspaceItem(context)?.let { folder.add(it) }
                    }
                    if (folder.getContents().isNotEmpty()) foldersToAdd.add(folder)
                }
            }

            Log.d(TAG, "Adding ${foldersToAdd.size} folders and ${singleItems.size} single apps")

            if (foldersToAdd.isNotEmpty()) {
                AddFoldersWithItemsTask(foldersToAdd) {
                    if (singleItems.isNotEmpty()) {
                        model.addAndBindAddedWorkspaceItems(singleItems)
                    }
                    onComplete?.invoke()
                }.execute(taskController, dataModel, taskController.allAppsList)
            } else if (singleItems.isNotEmpty()) {
                model.addAndBindAddedWorkspaceItems(singleItems)
                onComplete?.invoke()
            } else {
                onComplete?.invoke()
            }
        }
    }

    /**
     * Checks if a package already has an item on the workspace (desktop or inside a folder).
     * Uses [BgDataModel.itemsIdMap] which contains ALL items.
     */
    fun isAppOnWorkspace(packageName: String, user: UserHandle, dataModel: BgDataModel): Boolean {
        synchronized(dataModel) {
            for (item in dataModel.itemsIdMap) {
                if (item is WorkspaceItemInfo && item.user == user) {
                    if (item.targetComponent?.packageName == packageName) return true
                }
            }
        }
        return false
    }

    /**
     * Finds an existing folder on the workspace by category name.
     */
    fun findFolderByCategory(category: String, dataModel: BgDataModel): FolderInfo? {
        synchronized(dataModel) {
            for (item in dataModel.itemsIdMap) {
                if (item is FolderInfo && item.title?.toString() == category) {
                    return item
                }
            }
        }
        return null
    }

    private fun collectWorkspacePackages(dataModel: BgDataModel, user: UserHandle): Set<String> {
        val packages = mutableSetOf<String>()
        synchronized(dataModel) {
            for (item in dataModel.itemsIdMap) {
                if (item is WorkspaceItemInfo && item.user == user) {
                    item.targetComponent?.packageName?.let { packages.add(it) }
                }
            }
        }
        return packages
    }

    companion object {
        private const val TAG = "AllowedAppsWorkspace"
    }
}
