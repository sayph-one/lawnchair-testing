/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.quickstep.util;

import static com.android.launcher3.AbstractFloatingView.TYPE_ALL_APPS_EDU;
import static com.android.launcher3.AbstractFloatingView.getOpenView;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.HINT_STATE;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.LauncherState.OVERVIEW;
import static com.android.launcher3.util.NavigationMode.NO_BUTTON;
import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_COUNT;
import static com.android.launcher3.util.OnboardingPrefs.HOME_BOUNCE_SEEN;
import static com.android.launcher3.util.OnboardingPrefs.HOTSEAT_DISCOVERY_TIP_COUNT;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.Toast;

import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.LauncherPrefs;
import com.android.launcher3.LauncherState;
import com.android.launcher3.Utilities;
import com.android.launcher3.Workspace;
import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.hybridhotseat.HotseatPredictionController;
import com.android.launcher3.statemanager.StateManager;
import com.android.launcher3.statemanager.StateManager.StateListener;
import com.android.launcher3.uioverrides.QuickstepLauncher;
import com.android.launcher3.util.DisplayController;
import com.android.launcher3.views.Snackbar;
import com.android.quickstep.views.AllAppsEduView;

/**
 * Class to setup onboarding behavior for quickstep launcher
 */
public class QuickstepOnboardingPrefs {

    // Session-based counter for notification access prompt (resets on app restart)
    private static final int MAX_NOTIFICATION_PROMPTS_PER_SESSION = 4;
    private static int sNotificationAccessPromptCount = 0;

    /**
     * Sets up the initial onboarding behavior for the launcher
     */
    public static void setup(QuickstepLauncher launcher) {
        StateManager<LauncherState, Launcher> stateManager = launcher.getStateManager();
        if (!HOME_BOUNCE_SEEN.get(launcher)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    boolean swipeUpEnabled =
                            DisplayController.getNavigationMode(launcher).hasGestures;
                    LauncherState prevState = stateManager.getLastState();

                    if (((swipeUpEnabled && finalState == OVERVIEW) || (!swipeUpEnabled
                            && finalState == ALL_APPS && prevState == NORMAL) ||
                            HOME_BOUNCE_COUNT.hasReachedMax(launcher))) {
                        LauncherPrefs.get(launcher).put(HOME_BOUNCE_SEEN, true);
                        stateManager.removeStateListener(this);
                    }
                }
            });
        }

        if (!Utilities.isRunningInTestHarness()
                && !HOTSEAT_DISCOVERY_TIP_COUNT.hasReachedMax(launcher)) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                boolean mFromAllApps = false;

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    mFromAllApps = launcher.getStateManager().getCurrentStableState() == ALL_APPS;
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    HotseatPredictionController client = launcher.getHotseatPredictionController();
                    if (mFromAllApps && finalState == NORMAL && client.hasPredictions()) {
                        if (!launcher.getDeviceProfile().isTablet
                                && HOTSEAT_DISCOVERY_TIP_COUNT.increment(launcher)) {
                            client.showEdu();
                            stateManager.removeStateListener(this);
                        }
                    }
                }
            });
        }

        if (DisplayController.getNavigationMode(launcher) == NO_BUTTON) {
            stateManager.addStateListener(new StateListener<LauncherState>() {
                private static final int MAX_NUM_SWIPES_TO_TRIGGER_EDU = 3;

                // Counts the number of consecutive swipes on nav bar without moving screens.
                private int mCount = 0;
                private boolean mShouldIncreaseCount;

                @Override
                public void onStateTransitionStart(LauncherState toState) {
                    if (toState == NORMAL) {
                        return;
                    }
                    mShouldIncreaseCount = toState == HINT_STATE
                            && launcher.getWorkspace().getNextPage() == Workspace.DEFAULT_PAGE;
                }

                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == NORMAL) {
                        if (mCount >= MAX_NUM_SWIPES_TO_TRIGGER_EDU) {
                            if (getOpenView(launcher, TYPE_ALL_APPS_EDU) == null) {
                                AllAppsEduView.show(launcher);
                            }
                            mCount = 0;
                        }
                        return;
                    }

                    if (mShouldIncreaseCount && finalState == HINT_STATE) {
                        mCount++;
                    } else {
                        mCount = 0;
                    }

                    if (finalState == ALL_APPS) {
                        AllAppsEduView view = getOpenView(launcher, TYPE_ALL_APPS_EDU);
                        if (view != null) {
                            view.close(false);
                        }
                    }
                }
            });
        }

        if (!Utilities.isRunningInTestHarness()) {
            launcher.getStateManager().addStateListener(new StateListener<LauncherState>() {
                @Override
                public void onStateTransitionComplete(LauncherState finalState) {
                    if (finalState == ALL_APPS) {
                        ALL_APPS_VISITED_COUNT.increment(launcher);
                    }
                    launcher.getAppsView().getFloatingHeaderView()
                            .findFixedRowByType(AppsDividerView.class)
                            .setShowAllAppsLabel(!ALL_APPS_VISITED_COUNT.hasReachedMax(launcher));
                }
            });
        }

    }

    /**
     * Check and show notification access prompt if needed.
     * Call this from onResume() to prompt users who don't use state transitions.
     * Shows up to MAX_NOTIFICATION_PROMPTS_PER_SESSION times per app restart.
     */
    public static void checkNotificationAccessPrompt(Launcher launcher) {
        if (!isNotificationListenerEnabled(launcher)
                && sNotificationAccessPromptCount < MAX_NOTIFICATION_PROMPTS_PER_SESSION) {
            sNotificationAccessPromptCount++;
            // Delay slightly to avoid showing during initial layout
            launcher.getDragLayer().postDelayed(() -> {
                if (!isNotificationListenerEnabled(launcher)) {
                    showNotificationAccessSnackbar(launcher);
                }
            }, 1000);
        }
    }

    private static boolean isNotificationListenerEnabled(Context context) {
        String listeners = Settings.Secure.getString(
                context.getContentResolver(), "enabled_notification_listeners");
        return listeners != null && listeners.contains(context.getPackageName());
    }

    private static void showNotificationAccessSnackbar(Launcher launcher) {
        Snackbar.show(launcher,
                R.string.notification_access_prompt,
                R.string.notification_access_enable,
                null,
                () -> {
                    Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                    intent.putExtra(Settings.EXTRA_APP_PACKAGE, launcher.getPackageName());
                    launcher.startActivity(intent);
                    Toast.makeText(launcher,
                            R.string.notification_access_hint,
                            Toast.LENGTH_LONG).show();
                });
    }
}
