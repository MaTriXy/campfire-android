package com.pandulapeter.campfire.presentation.android.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.pandulapeter.campfire.shared.ui.CampfireViewModelStateHolder
import com.pandulapeter.campfire.shared.ui.screenComponents.settings.SettingsPlaceholder

@Composable
internal fun SettingsScreenAndroid(
    modifier: Modifier = Modifier,
    stateHolder: CampfireViewModelStateHolder
) = SettingsPlaceholder(
    modifier = modifier,
    uiStrings = stateHolder.uiStrings.value
)