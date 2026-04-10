/*
 * Copyright 2022, Lawnchair
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package app.lawnchair

import android.animation.AnimatorSet
import android.app.ActivityOptions
import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.util.Pair
import android.view.Display
import android.view.View
import android.view.ViewTreeObserver
import android.widget.Button
import android.window.SplashScreen
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import app.lawnchair.LawnchairApp.Companion.showQuickstepWarningIfNecessary
import app.lawnchair.compat.LawnchairQuickstepCompat
import app.lawnchair.data.AppDatabase
import app.lawnchair.data.wallpaper.service.WallpaperService
import app.lawnchair.deck.LawndeckManager
import app.lawnchair.factory.LawnchairWidgetHolder
import app.lawnchair.gestures.GestureController
import app.lawnchair.gestures.VerticalSwipeTouchController
import app.lawnchair.gestures.config.GestureHandlerConfig
import app.lawnchair.nexuslauncher.OverlayCallbackImpl
import app.lawnchair.preferences.PreferenceManager
import app.lawnchair.preferences2.PreferenceManager2
import app.lawnchair.root.RootHelperManager
import app.lawnchair.root.RootNotAvailableException
import app.lawnchair.theme.ThemeProvider
import app.lawnchair.ui.RegistrationOverlayManager
import app.lawnchair.ui.popup.LauncherOptionsPopup
import app.lawnchair.ui.popup.LawnchairShortcut
import app.lawnchair.util.AllowedApps
import app.lawnchair.util.getThemedIconPacksInstalled
import app.lawnchair.util.unsafeLazy
import app.lawnchair.views.LawnchairFloatingSurfaceView
import com.android.launcher3.AbstractFloatingView
import com.android.launcher3.BaseActivity
import com.android.launcher3.BubbleTextView
import com.android.launcher3.GestureNavContract
import com.android.launcher3.LauncherAppState
import com.android.launcher3.LauncherState
import com.android.launcher3.R
import com.android.launcher3.Utilities
import com.android.launcher3.model.data.ItemInfo
import com.android.launcher3.popup.SystemShortcut
import com.android.launcher3.shortcuts.DeepShortcutView
import com.android.launcher3.statemanager.StateManager
import com.android.launcher3.statemanager.StateManager.StateHandler
import com.android.quickstep.util.QuickstepOnboardingPrefs
import com.android.launcher3.uioverrides.QuickstepLauncher
import com.android.launcher3.uioverrides.states.AllAppsState
import com.android.launcher3.uioverrides.states.BackgroundAppState
import com.android.launcher3.uioverrides.states.OverviewState
import com.android.launcher3.util.ActivityOptionsWrapper
import com.android.launcher3.util.Executors
import com.android.launcher3.util.RunnableList
import com.android.launcher3.util.SystemUiController.UI_STATE_BASE_WINDOW
import com.android.launcher3.util.Themes
import com.android.launcher3.util.TouchController
import com.android.launcher3.views.ActivityContext
import com.android.launcher3.views.OptionsPopupView
import com.android.launcher3.views.OptionsPopupView.OptionItem
import com.android.launcher3.widget.LauncherWidgetHolder
import com.android.launcher3.widget.RoundedCornerEnforcement
import com.android.systemui.plugins.shared.LauncherOverlayManager
import com.android.systemui.shared.system.QuickStepContract
import com.kieronquinn.app.smartspacer.sdk.client.SmartspacerClient
import com.patrykmichalik.opto.core.firstBlocking
import com.patrykmichalik.opto.core.onEach
import dev.kdrag0n.monet.theme.ColorScheme
import java.util.stream.Stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LawnchairLauncher : QuickstepLauncher() {
    private val defaultOverlay by unsafeLazy { OverlayCallbackImpl(this) }
    private val prefs by unsafeLazy { PreferenceManager.getInstance(this) }
    private val preferenceManager2 by unsafeLazy { PreferenceManager2.getInstance(this) }
    private val insetsController by unsafeLazy { WindowInsetsControllerCompat(launcher.window, rootView) }
    private val themeProvider by unsafeLazy { ThemeProvider.INSTANCE.get(this) }
    private var registrationOverlayManager: RegistrationOverlayManager? = null
    private var downtimeOverlayManager: app.lawnchair.ui.DowntimeOverlayManager? = null
    private var statusReceiver: AllowedApps.RegistrationStatusReceiver? = null
    private var downtimeReceiver: AllowedApps.DowntimeStatusReceiver? = null
    private var pendingMissingAppsCheck = false
    private val noStatusBarStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is OverviewState) {
                insetsController.show(WindowInsetsCompat.Type.statusBars())
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState !is OverviewState) {
                insetsController.hide(WindowInsetsCompat.Type.statusBars())
            }
        }
    }

    private fun setWallpaperOnce() {
        val wallpaperManager = WallpaperManager.getInstance(this)
        val bitmap = BitmapFactory.decodeResource(this.resources, R.drawable.default_wallpaper)
        wallpaperManager.setBitmap(bitmap)
    }

    private val rememberPositionStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            if (toState is AllAppsState) {
                mAppsView.activeRecyclerView.restoreScrollPosition()
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val statusBarClockListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionStart(toState: LauncherState) {
            when (toState) {
                is BackgroundAppState,
                is OverviewState,
                is AllAppsState,
                -> {
                    LawnchairApp.instance.restoreClockInStatusBar()
                }

                else -> {
                    workspace.updateStatusbarClock()
                }
            }
        }
        override fun onStateTransitionComplete(finalState: LauncherState) {}
    }
    private val clearSearchStateListener = object : StateManager.StateListener<LauncherState> {
        override fun onStateTransitionComplete(finalState: LauncherState) {
            if (finalState == LauncherState.NORMAL && mAppsView != null && mAppsView.isSearching) {
                mAppsView?.post {
                    mAppsView.reset(false, true)
                }
            }
        }
    }

    private lateinit var colorScheme: ColorScheme
    private var hasBackGesture = false

    val gestureController by unsafeLazy { GestureController(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!Utilities.ATLEAST_Q) {
            enableEdgeToEdge(
                navigationBarStyle = SystemBarStyle.auto(
                    Color.TRANSPARENT,
                    Color.TRANSPARENT,
                ),
            )
        }
        layoutInflater.factory2 = LawnchairLayoutFactory(this)
        super.onCreate(savedInstanceState)

        prefs.launcherTheme.subscribeChanges(this, ::updateTheme)
        prefs.feedProvider.subscribeChanges(this, defaultOverlay::reconnect)
        preferenceManager2.enableFeed.get().distinctUntilChanged().onEach { enable ->
            defaultOverlay.setEnableFeed(enable)
        }.launchIn(scope = lifecycleScope)
        launcher.stateManager.addStateListener(clearSearchStateListener)

        if (prefs.autoLaunchRoot.get()) {
            lifecycleScope.launch {
                try {
                    RootHelperManager.INSTANCE.get(this@LawnchairLauncher)
                } catch (_: RootNotAvailableException) {
                }
            }
        }

        preferenceManager2.showStatusBar.get().distinctUntilChanged().onEach {
            with(insetsController) {
                if (it) {
                    show(WindowInsetsCompat.Type.statusBars())
                } else {
                    hide(WindowInsetsCompat.Type.statusBars())
                }
            }
            with(launcher.stateManager) {
                if (it) {
                    removeStateListener(noStatusBarStateListener)
                } else {
                    addStateListener(noStatusBarStateListener)
                }
            }
        }.launchIn(scope = lifecycleScope)

        preferenceManager2.statusBarClock.get().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(statusBarClockListener)
                } else {
                    removeStateListener(statusBarClockListener)
                    // Make sure status bar clock is restored when the preference is toggled off
                    LawnchairApp.instance.restoreClockInStatusBar()
                }
            }
        }
        preferenceManager2.rememberPosition.get().onEach {
            with(launcher.stateManager) {
                if (it) {
                    addStateListener(rememberPositionStateListener)
                } else {
                    removeStateListener(rememberPositionStateListener)
                }
            }
        }.launchIn(scope = lifecycleScope)

        prefs.overrideWindowCornerRadius.subscribeValues(this) {
            QuickStepContract.sHasCustomCornerRadius = it
        }
        prefs.windowCornerRadius.subscribeValues(this) {
            QuickStepContract.sCustomCornerRadius = it.toFloat()
        }
        preferenceManager2.roundedWidgets.onEach(launchIn = lifecycleScope) {
            RoundedCornerEnforcement.sRoundedCornerEnabled = it
        }
        val isWorkspaceDarkText = Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText)
        preferenceManager2.darkStatusBar.onEach(launchIn = lifecycleScope) { darkStatusBar ->
            systemUiController.updateUiState(UI_STATE_BASE_WINDOW, isWorkspaceDarkText || darkStatusBar)
        }
        preferenceManager2.backPressGestureHandler.onEach(launchIn = lifecycleScope) { handler ->
            hasBackGesture = handler !is GestureHandlerConfig.NoOp
        }

        LauncherOptionsPopup.restoreMissingPopupOptions(launcher)
        LauncherOptionsPopup.migrateLegacyPreferences(launcher)

        // Handle update from version 12 Alpha 4 to version 12 Alpha 5.
        if (
            prefs.themedIcons.get() &&
            packageManager.getThemedIconPacksInstalled(this).isEmpty()
        ) {
            prefs.themedIcons.set(newValue = false)
        }

        colorScheme = themeProvider.colorScheme

        showQuickstepWarningIfNecessary()

        reloadIconsIfNeeded()

        AppDatabase.INSTANCE.get(this).checkpointSync()

        val deckPrefs = getSharedPreferences("lawndeck_state", Context.MODE_PRIVATE)
        val currentVersionCode = packageManager.getPackageInfo(packageName, 0).versionCode.toString()
        val lastVersionCode = deckPrefs.getString("last_version_code", "")
        val deckModeInitialized = deckPrefs.getBoolean("deck_mode_initialized", false)

        Log.e("DeckDebug", "Current version: $currentVersionCode, Last version: $lastVersionCode")

        // Apply deck mode if version has changed
        if (!deckModeInitialized) {
            Log.d("DeckDebug", "First install - deck mode initialization")
            pendingMissingAppsCheck = true

            // Set preferences synchronously so they're applied before any model load.
            // Must use runBlocking since pref set() is suspend but we need this done
            // before the model loads (activity may be recreated by permission dialogs).
            kotlinx.coroutines.runBlocking {
                preferenceManager2.deckLayout.set(true)
                preferenceManager2.enableSmartspace.set(true)
                preferenceManager2.swipeUpGestureHandler.set(GestureHandlerConfig.NoOp)
            }
            prefs.addIconToHome.set(true)

            // Mark as initialized immediately to prevent re-entry on activity recreate.
            // App population happens via finishBindingItems() after the model loads.
            deckPrefs.edit()
                .putString("last_version_code", currentVersionCode)
                .putBoolean("deck_mode_initialized", true)
                .apply()
        } else if (currentVersionCode != lastVersionCode) {
            Log.d("DeckDebug", "Version update - preserving workspace")
            kotlinx.coroutines.runBlocking {
                preferenceManager2.deckLayout.set(true)
                preferenceManager2.enableSmartspace.set(true)
                preferenceManager2.swipeUpGestureHandler.set(GestureHandlerConfig.NoOp)
            }
            prefs.addIconToHome.set(true)
            deckPrefs.edit()
                .putString("last_version_code", currentVersionCode)
                .apply()
        }

        // ADD REGISTRATION OVERLAY INITIALIZATION HERE
        initializeRegistrationOverlay()
    }

    override fun collectStateHandlers(out: MutableList<StateHandler<LauncherState>>) {
        super.collectStateHandlers(out)
        out.add(SearchBarStateHandler(this))
    }

    override fun getSupportedShortcuts(): Stream<SystemShortcut.Factory<*>> = Stream.concat(
        super.getSupportedShortcuts(),
        Stream.concat(
            Stream.of(LawnchairShortcut.UNINSTALL, LawnchairShortcut.CUSTOMIZE),
            if (LawnchairApp.isRecentsEnabled) Stream.of(LawnchairShortcut.PAUSE_APPS) else Stream.empty(),
        ),
    )

    override fun updateTheme() {
        if (themeProvider.colorScheme != colorScheme) {
            recreate()
        } else {
            super.updateTheme()
        }
    }

    override fun createTouchControllers(): Array<TouchController> {
        val verticalSwipeController = VerticalSwipeTouchController(this, gestureController)
        return arrayOf<TouchController>(verticalSwipeController) + super.createTouchControllers()
    }

    override fun handleHomeTap() {
        gestureController.onHomePressed()
    }

    override fun registerBackDispatcher() {
        if (LawnchairApp.isAtleastT) {
            super.registerBackDispatcher()
        }
    }

    override fun bindItems(items: List<ItemInfo>, forceAnimateIcons: Boolean) {
        val inflatedItems = items.map { i ->
            Pair.create(
                i,
                itemInflater?.inflateItem(
                    i,
                    modelWriter,
                ),
            )
        }.toList()
        bindInflatedItems(inflatedItems, if (forceAnimateIcons) AnimatorSet() else null)
    }

    override fun handleGestureContract(intent: Intent?) {
        if (!LawnchairApp.isRecentsEnabled && prefs.enableGnc.get()) {
            val gnc = GestureNavContract.fromIntent(intent)
            if (gnc != null) {
                AbstractFloatingView.closeOpenViews(
                    this,
                    false,
                    AbstractFloatingView.TYPE_ICON_SURFACE,
                )
                LawnchairFloatingSurfaceView.show(this, gnc)
            }
        }
    }

    override fun onUiChangedWhileSleeping() {
        if (Utilities.ATLEAST_S) {
            super.onUiChangedWhileSleeping()
        }
    }

    override fun showDefaultOptions(x: Float, y: Float) {
        val showWallpaperCarousel = "+carousel" in preferenceManager2.launcherPopupOrder.firstBlocking()

        if (showWallpaperCarousel) {
            show<LawnchairLauncher>(
                this,
                getPopupTarget(x, y),
                OptionsPopupView.getOptions(this),
            )
        } else {
            super.showDefaultOptions(x, y)
        }
    }

    private fun <T> show(
        activityContext: ActivityContext?,
        targetRect: RectF,
        items: List<OptionItem>,
        shouldAddArrow: Boolean = false,
        width: Int = 0,
    ): OptionsPopupView<T>? where T : Context?, T : ActivityContext? {
        if (activityContext == null) return null

        val isEmpty = WallpaperService.INSTANCE.get(this).getTopWallpapers().isEmpty()
        val layout = if (isEmpty) R.layout.longpress_options_menu else R.layout.wallpaper_options_popup

        val popup = activityContext.layoutInflater.inflate(layout, activityContext.dragLayer, false) as OptionsPopupView<T>
        popup.setTargetRect(targetRect)
        popup.setShouldAddArrow(shouldAddArrow)

        for (item in items) {
            val deepLayout = if (isEmpty) R.layout.system_shortcut else R.layout.wallpaper_options_popup_item

            val view = popup.inflateAndAdd<DeepShortcutView>(deepLayout, popup)
            if (width > 0) view.layoutParams.width = width
            view.iconView.setBackgroundDrawable(item.icon)
            view.bubbleText.text = item.label
            view.setOnClickListener(popup)
            view.onLongClickListener = popup
            popup.mItemMap[view] = item
        }

        popup.show()
        return popup
    }

    override fun createAppWidgetHolder(): LauncherWidgetHolder {
        val factory = LauncherWidgetHolder.HolderFactory.newFactory(this) as LawnchairWidgetHolder.LawnchairHolderFactory
        return factory.newInstance(
            this,
        ) { appWidgetId: Int ->
            workspace.removeWidget(
                appWidgetId,
            )
        }
    }

    override fun makeDefaultActivityOptions(splashScreenStyle: Int): ActivityOptionsWrapper {
        val callbacks = RunnableList()
        val options = if (Utilities.ATLEAST_Q) {
            LawnchairQuickstepCompat.activityOptionsCompat.makeCustomAnimation(
                this,
                0,
                0,
                Executors.MAIN_EXECUTOR.handler,
                null,
            ) {
                callbacks.executeAllAndDestroy()
            }
        } else {
            ActivityOptions.makeBasic()
        }
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = splashScreenStyle
        }

        Utilities.allowBGLaunch(options)
        return ActivityOptionsWrapper(options, callbacks)
    }

    override fun getActivityLaunchOptions(v: View?, item: ItemInfo?): ActivityOptionsWrapper {
        return runCatching {
            super.getActivityLaunchOptions(v, item)
        }.getOrElse {
            getActivityLaunchOptionsDefault(v)
        }
    }

    private fun getActivityLaunchOptionsDefault(v: View?): ActivityOptionsWrapper {
        var left = 0
        var top = 0
        var width = v!!.measuredWidth
        var height = v.measuredHeight
        if (v is BubbleTextView) {
            // Launch from center of icon, not entire view
            val icon: Drawable? = v.icon
            if (icon != null) {
                val bounds = icon.bounds
                left = (width - bounds.width()) / 2
                top = v.paddingTop
                width = bounds.width()
                height = bounds.height()
            }
        }
        val options = Utilities.allowBGLaunch(
            ActivityOptions.makeClipRevealAnimation(
                v,
                left,
                top,
                width,
                height,
            ),
        )
        if (Utilities.ATLEAST_T) {
            options.splashScreenStyle = SplashScreen.SPLASH_SCREEN_STYLE_ICON
        }
        options.launchDisplayId = if (v.display != null) v.display.displayId else Display.DEFAULT_DISPLAY
        val callback = RunnableList()
        return ActivityOptionsWrapper(options, callback)
    }

    override fun onResume() {
        super.onResume()
        restartIfPending()

        // Check if we should prompt for notification access
        QuickstepOnboardingPrefs.checkNotificationAccessPrompt(this)

        dragLayer.viewTreeObserver.addOnDrawListener(
            object : ViewTreeObserver.OnDrawListener {
                private var handled = false

                override fun onDraw() {
                    if (handled) {
                        return
                    }
                    handled = true

                    dragLayer.post {
                        dragLayer.viewTreeObserver.removeOnDrawListener(this)
                    }
                    depthController
                }
            },
        )

        // ADD REGISTRATION STATUS CHECK HERE
        android.util.Log.d("LawnchairLauncher", "onResume() called - checking registration status")
        AllowedApps.updateRegistrationCache(this)
        app.lawnchair.util.DebugRegistrationHelper.logRegistrationState(this)
        registrationOverlayManager?.refreshStatus()

        // Check downtime status (only if registration overlay is not showing)
        if (registrationOverlayManager?.isOverlayCurrentlyVisible() != true) {
            downtimeOverlayManager?.refreshStatus()
        }

        // Missing apps are added via finishBindingItems() callback after model loads.
    }

    override fun onDestroy() {
        super.onDestroy()
        // Only actually closes if required, safe to call if not enabled
        SmartspacerClient.close()

        // ADD REGISTRATION OVERLAY CLEANUP HERE
        cleanupRegistrationOverlay()
    }

    override fun getDefaultOverlay(): LauncherOverlayManager = defaultOverlay

    fun recreateIfNotScheduled() {
        if (sRestartFlags == 0) {
            recreate()
        }
    }

    private fun initializeRegistrationOverlay() {
        try {
            AllowedApps.updateRegistrationCache(this)
            app.lawnchair.util.DebugRegistrationHelper.logRegistrationState(this)

            registrationOverlayManager = RegistrationOverlayManager(this, dragLayer)

            // THIS IS THE KEY CHANGE - use the new method
            registrationOverlayManager?.onRegistrationChanged = {
                android.util.Log.d("LawnchairLauncher", "Registration changed - triggering workspace reload")
                reloadWorkspaceOnRegistrationChange()  // Call the new method
            }

            statusReceiver = AllowedApps.registerStatusReceiver(this)

            AllowedApps.addRegistrationListener {
                android.util.Log.d("LawnchairLauncher", "=== REGISTRATION LISTENER TRIGGERED ===")
                registrationOverlayManager?.updateOverlayVisibility()
                // Also check downtime when registration changes (device just registered -> check downtime)
                downtimeOverlayManager?.updateOverlayVisibility()
                reloadWorkspaceOnRegistrationChange()  // Call the new method here too
            }

            // Initialize downtime overlay
            downtimeOverlayManager = app.lawnchair.ui.DowntimeOverlayManager(this, dragLayer)
            downtimeReceiver = AllowedApps.registerDowntimeReceiver(this)

            AllowedApps.addDowntimeListener {
                android.util.Log.d("LawnchairLauncher", "=== DOWNTIME LISTENER TRIGGERED ===")
                downtimeOverlayManager?.updateOverlayVisibility()
                reloadWorkspaceOnRegistrationChange()
            }

            registrationOverlayManager?.updateOverlayVisibility()
            // Only show downtime overlay if registration overlay is not showing
            if (registrationOverlayManager?.isOverlayCurrentlyVisible() != true) {
                downtimeOverlayManager?.updateOverlayVisibility()
            }

            startPeriodicDebugCheck()

        } catch (e: Exception) {
            android.util.Log.e("LawnchairLauncher", "Failed to initialize registration overlay", e)
        }
    }

    private fun setRegistrationStatusForTesting(isRegistered: Boolean) {
        try {
            val prefs = getSharedPreferences("sayph_device_status", Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("device_registered", isRegistered)
                .putLong("last_update_timestamp", System.currentTimeMillis())
                .apply()

            android.util.Log.d("LawnchairLauncher", "TESTING: Set registration status to $isRegistered")

            // Force refresh the overlay
            AllowedApps.updateRegistrationCache(this)
            registrationOverlayManager?.refreshStatus()

        } catch (e: Exception) {
            android.util.Log.e("LawnchairLauncher", "Failed to set test registration status", e)
        }
    }

    /**
     * Helper method to check if an object has a specific method
     */
    private fun hasMethod(obj: Any, methodName: String): Boolean {
        return try {
            obj.javaClass.getMethod(methodName) != null
        } catch (e: NoSuchMethodException) {
            false
        }
    }

    /**
     * Called by AOSP when the model finishes binding all workspace items.
     * Only populates missing allowed apps when triggered by registration change,
     * not on every bind — otherwise user-removed items get re-added.
     */
    override fun finishBindingItems(pagesBoundFirst: com.android.launcher3.util.IntSet) {
        super.finishBindingItems(pagesBoundFirst)
        model?.enqueueModelUpdateTask { _, dataModel, allApps ->
            val workspaceEmpty = synchronized(dataModel) { dataModel.itemsIdMap.size() == 0 }
            if (pendingMissingAppsCheck || workspaceEmpty) {
                pendingMissingAppsCheck = false
                app.lawnchair.workspace.AllowedAppsWorkspaceManager(this@LawnchairLauncher)
                    .ensureMissingAppsOnWorkspace(model!!, dataModel, allApps)
            }
        }
    }

    /**
     * Reload workspace when registration status changes
     */
    private fun reloadWorkspaceOnRegistrationChange() {
        android.util.Log.d("LawnchairLauncher", "Registration changed — reloading workspace")
        pendingMissingAppsCheck = true
        model?.forceReload()
    }

    /**
     * Refresh the apps list when registration status changes
     */
    private fun refreshAppsList() {
        try {
            android.util.Log.d("LawnchairLauncher", "Attempting to refresh apps list")

            // Approach 1: Try to refresh the apps view directly
            appsView?.let { appsView ->
                android.util.Log.d("LawnchairLauncher", "Refreshing via appsView")
                try {
                    // Try common app store refresh methods
                    appsView.appsStore?.let { appsStore ->
                        // Try different possible method names
                        when {
                            // Try to find a reload or refresh method
                            hasMethod(appsStore, "reload") -> {
                                appsStore.javaClass.getMethod("reload").invoke(appsStore)
                                android.util.Log.d("LawnchairLauncher", "Called appsStore.reload()")
                            }
                            hasMethod(appsStore, "refresh") -> {
                                appsStore.javaClass.getMethod("refresh").invoke(appsStore)
                                android.util.Log.d("LawnchairLauncher", "Called appsStore.refresh()")
                            }
                            hasMethod(appsStore, "reloadApps") -> {
                                appsStore.javaClass.getMethod("reloadApps").invoke(appsStore)
                                android.util.Log.d("LawnchairLauncher", "Called appsStore.reloadApps()")
                            }
                            else -> {
                                android.util.Log.d("LawnchairLauncher", "No known refresh methods found on appsStore")
                            }
                        }
                    }

                    // Try refreshing the apps view itself
                    when {
                        hasMethod(appsView, "refresh") -> {
                            appsView.javaClass.getMethod("refresh").invoke(appsView)
                            android.util.Log.d("LawnchairLauncher", "Called appsView.refresh()")
                        }
                        hasMethod(appsView, "reloadApps") -> {
                            appsView.javaClass.getMethod("reloadApps").invoke(appsView)
                            android.util.Log.d("LawnchairLauncher", "Called appsView.reloadApps()")
                        }
                        else -> {
                            android.util.Log.d("LawnchairLauncher", "No known refresh methods found on appsView")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LawnchairLauncher", "appsView refresh failed", e)
                }
            } ?: android.util.Log.d("LawnchairLauncher", "appsView is null")

            // Approach 2: Try to refresh via model
            model?.let { model ->
                try {
                    android.util.Log.d("LawnchairLauncher", "Refreshing via model")
                    when {
                        hasMethod(model, "forceReload") -> {
                            model.javaClass.getMethod("forceReload").invoke(model)
                            android.util.Log.d("LawnchairLauncher", "Called model.forceReload()")
                        }
                        hasMethod(model, "reload") -> {
                            model.javaClass.getMethod("reload").invoke(model)
                            android.util.Log.d("LawnchairLauncher", "Called model.reload()")
                        }
                        else -> {
                            android.util.Log.d("LawnchairLauncher", "No known reload methods found on model")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("LawnchairLauncher", "model refresh failed", e)
                }
            } ?: android.util.Log.d("LawnchairLauncher", "model is null")

            // Approach 3: Try the nuclear option - recreate activity
            try {
                android.util.Log.d("LawnchairLauncher", "Using nuclear option - recreating activity")
                runOnUiThread {
                    recreate()
                }
            } catch (e: Exception) {
                android.util.Log.w("LawnchairLauncher", "recreate failed", e)
            }

            android.util.Log.d("LawnchairLauncher", "App list refresh attempts completed")

        } catch (e: Exception) {
            android.util.Log.e("LawnchairLauncher", "Failed to refresh apps list", e)
        }
    }


    fun manualRefreshApps() {
        android.util.Log.d("LawnchairLauncher", "=== MANUAL REFRESH TRIGGERED ===")
        refreshAppsList()
    }

    private fun startPeriodicDebugCheck() {
        // Check registration status every 5 seconds for debugging using Handler
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val debugRunnable = object : Runnable {
            override fun run() {
                android.util.Log.d("LawnchairLauncher", "=== PERIODIC DEBUG CHECK ===")
                AllowedApps.updateRegistrationCache(this@LawnchairLauncher)
                app.lawnchair.util.DebugRegistrationHelper.logRegistrationState(this@LawnchairLauncher)
                registrationOverlayManager?.refreshStatus()

                // Check downtime (only if registration overlay is not showing)
                if (registrationOverlayManager?.isOverlayCurrentlyVisible() != true) {
                    downtimeOverlayManager?.refreshStatus()
                }

                // Schedule next check
                handler.postDelayed(this, 5000)
            }
        }

        // Start the first check
        handler.postDelayed(debugRunnable, 5000)
    }

    private fun cleanupRegistrationOverlay() {
        try {
            // Clean up overlay managers
            registrationOverlayManager?.destroy()
            registrationOverlayManager = null

            downtimeOverlayManager?.destroy()
            downtimeOverlayManager = null

            // Unregister broadcast receivers
            statusReceiver?.let { receiver ->
                try {
                    unregisterReceiver(receiver)
                } catch (e: Exception) {
                    android.util.Log.w("LawnchairLauncher", "Failed to unregister status receiver", e)
                }
            }
            statusReceiver = null

            downtimeReceiver?.let { receiver ->
                try {
                    unregisterReceiver(receiver)
                } catch (e: Exception) {
                    android.util.Log.w("LawnchairLauncher", "Failed to unregister downtime receiver", e)
                }
            }
            downtimeReceiver = null

        } catch (e: Exception) {
            android.util.Log.e("LawnchairLauncher", "Failed to cleanup overlays", e)
        }
    }

    fun refreshRegistrationStatus() {
        registrationOverlayManager?.refreshStatus()
    }

    fun forceShowRegistrationOverlay() {
        android.util.Log.d("LawnchairLauncher", "forceShowRegistrationOverlay() called for testing")
        registrationOverlayManager?.let { manager ->
            // Force create fallback overlay for testing
            try {
                val method = manager.javaClass.getDeclaredMethod("createFallbackOverlay")
                method.isAccessible = true
                method.invoke(manager)
                android.util.Log.d("LawnchairLauncher", "Successfully called createFallbackOverlay for testing")
            } catch (e: Exception) {
                android.util.Log.e("LawnchairLauncher", "Failed to call createFallbackOverlay for testing", e)
            }
        }
    }

    /**
     * Debug method to toggle the registration overlay visibility
     */
    fun toggleRegistrationOverlay() {
        android.util.Log.d("LawnchairLauncher", "toggleRegistrationOverlay() called for testing")
        registrationOverlayManager?.toggleOverlay()
    }

    /**
     * Debug method to check if overlay is currently visible
     */
    fun isRegistrationOverlayVisible(): Boolean {
        return registrationOverlayManager?.isOverlayCurrentlyVisible() ?: false
    }

    /**
     * Debug only: preview the downtime overlay with a given routine type.
     * Types: bedtime, school, dinner, custom.
     */
    fun debugPreviewDowntimeOverlay(routineType: String) {
        android.util.Log.d("LawnchairLauncher", "debugPreviewDowntimeOverlay($routineType)")
        downtimeOverlayManager?.debugPreview(routineType)
    }

    /** Debug only: hide the downtime overlay if currently shown. */
    fun debugHideDowntimeOverlay() {
        downtimeOverlayManager?.debugHide()
    }

    fun isDowntimeOverlayVisible(): Boolean {
        return downtimeOverlayManager?.isOverlayCurrentlyVisible() ?: false
    }

    private fun restartIfPending() {
        when {
            sRestartFlags and FLAG_RESTART != 0 -> lawnchairApp.restart(false)

            sRestartFlags and FLAG_RECREATE != 0 -> {
                sRestartFlags = 0
                recreate()
            }
        }
    }

    /**
     * Reloads app icons if there is an active icon pack & [PreferenceManager2.alwaysReloadIcons] is enabled.
     */
    private fun reloadIconsIfNeeded() {
        if (
            preferenceManager2.alwaysReloadIcons.firstBlocking()
        ) {
            LauncherAppState.getInstance(this).reloadIcons()
        }
    }

    companion object {
        private const val FLAG_RECREATE = 1 shl 0
        private const val FLAG_RESTART = 1 shl 1

        var sRestartFlags = 0

        val instance get() = LauncherAppState.getInstanceNoCreate()?.launcher as? LawnchairLauncher
    }
}

val Context.launcher: LawnchairLauncher
    get() = BaseActivity.fromContext(this)

val Context.launcherNullable: LawnchairLauncher? get() = try {
    launcher
} catch (_: IllegalArgumentException) {
    null
}
