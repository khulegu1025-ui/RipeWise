package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppLanguage
import com.example.model.SpatialScaleHint
import com.example.model.UserPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ripewise_preferences")

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val LANGUAGE = stringPreferencesKey("app_language")
        val DIETARY_GOALS = stringSetPreferencesKey("dietary_goals")
        val NUTRIENT_NEEDS = stringSetPreferencesKey("nutrient_needs")
        val HEALTH_FOCUSES = stringSetPreferencesKey("health_focuses")
        val COOKING_TOOLS = stringSetPreferencesKey("cooking_tools")
        val LIGHTING_CALIBRATION = booleanPreferencesKey("lighting_calibration")
        val SPATIAL_MODE = booleanPreferencesKey("spatial_mode")
        val SPATIAL_SCALE_HINT = stringPreferencesKey("spatial_scale_hint")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        val defaults = UserPreferences()
        val langCode = prefs[Keys.LANGUAGE] ?: defaults.language.code
        val language = AppLanguage.entries.firstOrNull { it.code == langCode } ?: defaults.language
        val hintName = prefs[Keys.SPATIAL_SCALE_HINT] ?: defaults.spatialScaleHint.name
        val scaleHint = SpatialScaleHint.entries.firstOrNull { it.name == hintName } ?: defaults.spatialScaleHint

        UserPreferences(
            language = language,
            dietaryGoals = prefs[Keys.DIETARY_GOALS] ?: defaults.dietaryGoals,
            nutrientNeeds = prefs[Keys.NUTRIENT_NEEDS] ?: defaults.nutrientNeeds,
            healthFocuses = prefs[Keys.HEALTH_FOCUSES] ?: defaults.healthFocuses,
            cookingTools = prefs[Keys.COOKING_TOOLS] ?: defaults.cookingTools,
            lightingCalibrationEnabled = prefs[Keys.LIGHTING_CALIBRATION] ?: defaults.lightingCalibrationEnabled,
            spatialModeEnabled = prefs[Keys.SPATIAL_MODE] ?: defaults.spatialModeEnabled,
            spatialScaleHint = scaleHint
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE] = language.code
        }
    }

    suspend fun toggleDietaryGoal(goal: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.DIETARY_GOALS] ?: UserPreferences().dietaryGoals
            prefs[Keys.DIETARY_GOALS] = if (goal in current) current - goal else current + goal
        }
    }

    suspend fun toggleNutrientNeed(need: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.NUTRIENT_NEEDS] ?: UserPreferences().nutrientNeeds
            prefs[Keys.NUTRIENT_NEEDS] = if (need in current) current - need else current + need
        }
    }

    suspend fun toggleHealthFocus(focus: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HEALTH_FOCUSES] ?: UserPreferences().healthFocuses
            prefs[Keys.HEALTH_FOCUSES] = if (focus in current) current - focus else current + focus
        }
    }

    suspend fun toggleCookingTool(tool: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.COOKING_TOOLS] ?: UserPreferences().cookingTools
            prefs[Keys.COOKING_TOOLS] = if (tool in current) current - tool else current + tool
        }
    }

    suspend fun setLightingCalibration(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LIGHTING_CALIBRATION] = enabled
        }
    }

    suspend fun setSpatialMode(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPATIAL_MODE] = enabled
        }
    }

    suspend fun setSpatialScaleHint(hint: SpatialScaleHint) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SPATIAL_SCALE_HINT] = hint.name
        }
    }
}
