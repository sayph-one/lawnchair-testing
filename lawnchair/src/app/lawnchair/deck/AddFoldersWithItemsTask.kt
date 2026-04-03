package app.lawnchair.deck

import android.content.Context
import android.os.UserHandle
import com.android.launcher3.LauncherModel
import com.android.launcher3.LauncherSettings
import com.android.launcher3.model.AllAppsList
import com.android.launcher3.model.BgDataModel
import com.android.launcher3.model.ModelTaskController
import com.android.launcher3.model.WorkspaceItemSpaceFinder
import com.android.launcher3.model.data.FolderInfo
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.model.data.WorkspaceItemInfo
import com.android.launcher3.util.IntArray

/**
 * Model task that adds folders with their items to the workspace.
 * Uses AOSP's [WorkspaceItemSpaceFinder] for cell placement and [ModelWriter] for DB writes.
 */
class AddFoldersWithItemsTask(
    private val folders: List<FolderInfo>,
    private val onComplete: (() -> Unit)? = null,
) : LauncherModel.ModelUpdateTask {

    private val itemSpaceFinder = WorkspaceItemSpaceFinder()

    override fun execute(
        taskController: ModelTaskController,
        dataModel: BgDataModel,
        apps: AllAppsList,
    ) {
        if (folders.isEmpty()) return

        val addedItemsFinal = ArrayList<ItemInfo>()
        val addedWorkspaceScreensFinal = IntArray()

        synchronized(dataModel) {
            val workspaceScreens = dataModel.collectWorkspaceScreens()
            val modelWriter = taskController.getModelWriter()

            folders.forEach { folderInfo ->
                val coords = itemSpaceFinder.findSpaceForItem(
                    taskController.app,
                    dataModel,
                    workspaceScreens,
                    addedWorkspaceScreensFinal,
                    folderInfo.spanX,
                    folderInfo.spanY,
                )

                modelWriter.addItemToDatabase(
                    folderInfo,
                    LauncherSettings.Favorites.CONTAINER_DESKTOP,
                    coords[0],
                    coords[1],
                    coords[2],
                )

                folderInfo.getContents().forEachIndexed { index, item ->
                    if (item is WorkspaceItemInfo) {
                        modelWriter.addOrMoveItemInDatabase(
                            item,
                            folderInfo.id,
                            0,
                            index % 4,
                            index / 4,
                        )
                    }
                }

                addedItemsFinal.add(folderInfo)
            }
        }

        if (addedItemsFinal.isNotEmpty()) {
            taskController.scheduleCallbackTask { callbacks ->
                val addAnimated = ArrayList<ItemInfo>()
                val addNotAnimated = ArrayList<ItemInfo>()
                val lastScreenId = addedItemsFinal.last().screenId

                addedItemsFinal.forEach { item ->
                    if (item.screenId == lastScreenId) {
                        addAnimated.add(item)
                    } else {
                        addNotAnimated.add(item)
                    }
                }

                callbacks.bindAppsAdded(
                    addedWorkspaceScreensFinal,
                    ArrayList(addNotAnimated),
                    ArrayList(addAnimated),
                )
                onComplete?.invoke()
            }
        } else {
            onComplete?.invoke()
        }
    }
}
