/*
 * Copyright 2024, Lawnchair
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

package app.lawnchair.preferences2

import androidx.datastore.core.DataMigration
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey

/**
 * Migration to enable notification count display by default for existing users.
 * This runs once and sets show_notification_count to true.
 */
class EnableNotificationCountMigration : DataMigration<Preferences> {

    private val showNotificationCountKey = booleanPreferencesKey("show_notification_count")
    private val migrationVersionKey = intPreferencesKey("preferences_migration_version")

    // Increment this when adding new migrations
    private val currentMigrationVersion = 1

    override suspend fun shouldMigrate(currentData: Preferences): Boolean {
        val version = currentData[migrationVersionKey] ?: 0
        return version < currentMigrationVersion
    }

    override suspend fun migrate(currentData: Preferences): Preferences {
        val mutablePreferences = currentData.toMutablePreferences()
        val currentVersion = currentData[migrationVersionKey] ?: 0

        // Migration v1: Enable notification count by default
        if (currentVersion < 1) {
            mutablePreferences[showNotificationCountKey] = true
        }

        // Update migration version
        mutablePreferences[migrationVersionKey] = currentMigrationVersion

        return mutablePreferences.toPreferences()
    }

    override suspend fun cleanUp() {
        // No cleanup needed
    }
}
