package app.lawnchair.deck

import android.content.Context
import android.util.Log
import app.lawnchair.LawnchairLauncher
import app.lawnchair.launcher
import app.lawnchair.launcherNullable
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.restartLauncher
import app.lawnchair.workspace.AllowedAppsWorkspaceManager
import com.android.launcher3.InvariantDeviceProfile
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelDbController
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.data.AppInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.provider.RestoreDbTask
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LawndeckManager(private val context: Context) {

    private val launcher = context.launcherNullable ?: LawnchairLauncher.instance?.launcher

    suspend fun enableLawndeck(
        onProgress: ((String) -> Unit)? = null,
    ) = withContext(Dispatchers.IO) {
        val completionDeferred = CompletableDeferred<Unit>()

        if (!backupExists("bk")) createBackup("bk")
        if (backupExists("lawndeck")) {
            onProgress?.invoke("Restoring previous layout...")
            restoreBackup("lawndeck")
            completionDeferred.complete(Unit)
        } else {
            onProgress?.invoke("Categorizing apps...")
            val model = launcher?.model
            if (model != null) {
                AllowedAppsWorkspaceManager(context)
                    .ensureMissingAppsOnWorkspaceCategorized(model, onProgress) {
                        completionDeferred.complete(Unit)
                    }
            } else {
                completionDeferred.complete(Unit)
            }
        }

        completionDeferred.await()
    }

    suspend fun disableLawndeck() = withContext(Dispatchers.IO) {
        if (backupExists("bk")) {
            createBackup("lawndeck")
            restoreBackup("bk")
        }
    }

    /**
     * Adds a newly installed allowed app to the workspace.
     * Called from PackageUpdatedTask when OP_ADD fires and deck mode is enabled.
     */
    fun addNewlyInstalledApp(
        packageName: String,
        user: android.os.UserHandle,
        taskController: ModelTaskController,
        dataModel: BgDataModel,
    ) {
        if (!AllowedApps.isInAllowedList(packageName)) return

        val manager = AllowedAppsWorkspaceManager(context)
        if (manager.isAppOnWorkspace(packageName, user, dataModel)) return

        val launcherApps = context.getSystemService(android.content.pm.LauncherApps::class.java)
            ?: return
        val activities = launcherApps.getActivityList(packageName, user)
        if (activities.isEmpty()) return

        val appInfo = AppInfo(context, activities[0], user)
        taskController.app.iconCache.getTitleAndIcon(appInfo, false)
        val wsi = appInfo.makeWorkspaceItem(context) ?: return

        // Try to add to an existing category folder
        val category = categorizeApp(appInfo)
        val existingFolder = manager.findFolderByCategory(category, dataModel)

        if (existingFolder != null) {
            existingFolder.add(wsi)
            taskController.getModelWriter().addOrMoveItemInDatabase(
                wsi,
                existingFolder.id,
                0,
                existingFolder.getContents().size % 4,
                existingFolder.getContents().size / 4,
            )
        } else {
            // Add as standalone via AOSP pipeline (handles space-finding and dedup)
            val itemList = listOf(android.util.Pair.create<com.android.launcher3.model.data.ItemInfo, Any?>(wsi, null))
            taskController.app.model.addAndBindAddedWorkspaceItems(itemList)
        }
    }

    private fun categorizeApp(appInfo: AppInfo): String {
        val packageName = appInfo.targetPackage ?: return "Other"
        return when {
            packageName.startsWith("com.google.") -> "Google Apps"
            appInfo.intent != null &&
                com.android.launcher3.util.PackageManagerHelper.isSystemApp(context, appInfo.intent) ->
                "System Apps"
            else -> {
                val potsManager = app.lawnchair.flowerpot.Flowerpot.Manager.getInstance(context)
                val categorized = potsManager.categorizeApps(listOf(appInfo))
                categorized.entries.firstOrNull()?.key ?: "Other"
            }
        }
    }

    private fun createBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            db.copyTo(backupDb, overwrite = true)
            if (journal.exists()) journal.copyTo(backupJournal, overwrite = true)
        }
    }.onFailure { Log.e(TAG, "Failed to create backup: $suffix", it) }

    private fun restoreBackup(suffix: String) = runCatching {
        getDatabaseFiles(suffix).apply {
            backupDb.copyTo(db, overwrite = true)
            if (backupJournal.exists()) backupJournal.copyTo(journal, overwrite = true)
        }
        postRestoreActions()
    }.onFailure { Log.e(TAG, "Failed to restore backup: $suffix", it) }

    private fun getDatabaseFiles(suffix: String): DatabaseFiles {
        val idp = InvariantDeviceProfile.INSTANCE.get(context)
        val dbFile = context.getDatabasePath(idp.dbFile)
        return DatabaseFiles(
            db = dbFile,
            backupDb = File(dbFile.parent, "${suffix}_${idp.dbFile}"),
            journal = File(dbFile.parent, "${idp.dbFile}-journal"),
            backupJournal = File(dbFile.parent, "${suffix}_${idp.dbFile}-journal"),
        )
    }

    private fun backupExists(suffix: String): Boolean = getDatabaseFiles(suffix).backupDb.exists()

    private fun postRestoreActions() {
        ModelDbController(context).let { RestoreDbTask.performRestore(context, it) }
        restartLauncher(context)
    }

    private data class DatabaseFiles(
        val db: File,
        val backupDb: File,
        val journal: File,
        val backupJournal: File,
    )

    companion object {
        private const val TAG = "LawndeckManager"
    }
}
