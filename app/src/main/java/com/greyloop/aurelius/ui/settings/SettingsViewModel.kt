package com.greyloop.aurelius.ui.settings

import androidx.lifecycle.ViewModel
import com.greyloop.aurelius.data.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsUiState(
    val minimaxApiKey: String = "",
    val codingPlanKey: String = "",
    val region: String = SecureStorage.REGION_GLOBAL,
    val planType: String = SecureStorage.PLAN_STANDARD,
    val setupComplete: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null
)

class SettingsViewModel(
    private val secureStorage: SecureStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    private fun loadSettings() {
        _uiState.value = SettingsUiState(
            minimaxApiKey = secureStorage.minimaxApiKey,
            codingPlanKey = secureStorage.codingPlanKey,
            region = secureStorage.region,
            planType = secureStorage.planType,
            setupComplete = secureStorage.setupComplete
        )
    }

    fun onMinimaxApiKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(minimaxApiKey = key)
    }

    fun onCodingPlanKeyChange(key: String) {
        _uiState.value = _uiState.value.copy(codingPlanKey = key)
    }

    fun onRegionChange(region: String) {
        _uiState.value = _uiState.value.copy(region = region)
    }

    fun onPlanTypeChange(planType: String) {
        _uiState.value = _uiState.value.copy(planType = planType)
    }

    fun saveSettings() {
        _uiState.value = _uiState.value.copy(isSaving = true)

        try {
            secureStorage.minimaxApiKey = _uiState.value.minimaxApiKey
            secureStorage.codingPlanKey = _uiState.value.codingPlanKey
            secureStorage.region = _uiState.value.region
            secureStorage.planType = _uiState.value.planType
            secureStorage.setupComplete = true

            _uiState.value = _uiState.value.copy(
                isSaving = false,
                setupComplete = true,
                message = "Settings saved successfully"
            )
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isSaving = false,
                message = "Failed to save settings: ${e.message}"
            )
        }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
