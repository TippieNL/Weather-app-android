package com.weatherquips.app.ui.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import com.weatherquips.app.locale.AppLanguage
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.weatherquips.app.R
import com.weatherquips.app.domain.model.DateFormat
import com.weatherquips.app.domain.model.LocationMode
import com.weatherquips.app.domain.model.TemperatureUnit
import com.weatherquips.app.domain.model.TimeFormat
import com.weatherquips.app.domain.model.WeatherService
import com.weatherquips.app.ui.theme.LocalAccents
import kotlinx.coroutines.launch

/**
 * Native settings, one screen per the Android convention (the web app used a
 * slide-in sheet). Everything writes straight through to DataStore, so there is
 * no "save" button and nothing to lose on process death.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    uiState: SettingsUiState,
    actions: SettingsActions,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenAbout: () -> Unit = {},
) {
    val settings = uiState.settings
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val accents = LocalAccents.current
    val blockedMessage = stringResource(R.string.notifications_blocked)
    val activity = LocalContext.current.findActivity()

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> actions.setNotificationsEnabled(enabled = granted, granted = granted) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings),
                        style = MaterialTheme.typography.titleLarge,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_SETTINGS_BACK)) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_left),
                            contentDescription = stringResource(R.string.back),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag(TAG_SETTINGS_SCREEN),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionTitle(stringResource(R.string.language))
            SettingsDropdown(
                label = stringResource(R.string.app_language),
                options = AppLanguage.entries,
                selected = uiState.language,
                // Each language in its own name, so it can be found by someone
                // who cannot read the one currently on screen.
                optionLabel = { it.endonym() ?: stringResource(R.string.language_system) },
                onSelect = { language ->
                    // Android 13+ recreates the screen itself; below that the
                    // new language only appears once the activity is rebuilt.
                    if (actions.setLanguage(language)) activity?.recreate()
                },
                testTag = TAG_LANGUAGE_DROPDOWN,
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.temperature_unit))
            SettingsDropdown(
                label = stringResource(R.string.unit),
                options = TemperatureUnit.entries,
                selected = settings.temperatureUnit,
                optionLabel = {
                    stringResource(
                        if (it == TemperatureUnit.CELSIUS) R.string.celsius else R.string.fahrenheit,
                    )
                },
                onSelect = actions::setTemperatureUnit,
                testTag = TAG_UNIT_DROPDOWN,
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.date_and_time))
            SettingsDropdown(
                label = stringResource(R.string.time_format),
                options = TimeFormat.entries,
                selected = settings.timeFormat,
                optionLabel = {
                    stringResource(if (it == TimeFormat.H12) R.string.hour_12 else R.string.hour_24)
                },
                onSelect = actions::setTimeFormat,
                testTag = TAG_TIME_DROPDOWN,
            )
            SettingsDropdown(
                label = stringResource(R.string.date_format),
                options = DateFormat.entries,
                selected = settings.dateFormat,
                optionLabel = { it.id },
                onSelect = actions::setDateFormat,
                testTag = TAG_DATE_DROPDOWN,
            )

            SectionDivider()
            SectionTitle(stringResource(R.string.location))
            SettingsDropdown(
                label = stringResource(R.string.mode),
                options = LocationMode.entries,
                selected = settings.locationMode,
                optionLabel = {
                    stringResource(if (it == LocationMode.DEVICE) R.string.device_gps else R.string.manual)
                },
                onSelect = actions::setLocationMode,
                testTag = TAG_LOCATION_MODE_DROPDOWN,
            )

            if (settings.locationMode == LocationMode.MANUAL) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = uiState.manualLocationInput,
                        onValueChange = actions::setManualLocation,
                        label = { Text(stringResource(R.string.city_or_place)) },
                        placeholder = { Text(stringResource(R.string.enter_city_name)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { actions.findLocation() }),
                        modifier = Modifier
                            .weight(1f)
                            .testTag(TAG_CITY_FIELD),
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = actions::findLocation,
                        enabled = uiState.geocodeState !is GeocodeState.Loading,
                        modifier = Modifier.testTag(TAG_FIND_BUTTON),
                    ) {
                        Text(stringResource(R.string.find))
                    }
                }

                GeocodeStatus(
                    state = uiState.geocodeState,
                    onSelect = actions::selectResult,
                )
            }

            if (settings.pokemonMode) {
                OutlinedButton(
                    onClick = actions::exitPokemonMode,
                    modifier = Modifier.testTag(TAG_EXIT_POKEMON),
                ) {
                    Text(stringResource(R.string.exit_pokemon_mode), color = accents.pokemonRed)
                }
            }

            SectionDivider()
            SectionTitle(stringResource(R.string.weather_service))
            SettingsDropdown(
                label = stringResource(R.string.provider),
                options = WeatherService.entries,
                selected = settings.weatherService,
                optionLabel = {
                    stringResource(
                        when (it) {
                            WeatherService.OPEN_METEO -> R.string.open_meteo_free
                            WeatherService.OPEN_WEATHER_MAP -> R.string.openweathermap
                            WeatherService.WEATHER_API -> R.string.weatherapi
                        },
                    )
                },
                onSelect = actions::setWeatherService,
                testTag = TAG_SERVICE_DROPDOWN,
            )

            if (settings.weatherService.requiresApiKey) {
                OutlinedTextField(
                    value = uiState.apiKeyInput,
                    onValueChange = actions::setApiKey,
                    label = { Text(stringResource(R.string.api_key)) },
                    placeholder = { Text(stringResource(R.string.enter_api_key)) },
                    singleLine = true,
                    // The key is a secret: masked on screen and never logged.
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    supportingText = { Text(stringResource(R.string.api_key_hint)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_API_KEY_FIELD),
                )
            }

            SectionDivider()
            SectionTitle(stringResource(R.string.notifications))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.precipitation_alerts),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        stringResource(R.string.precipitation_alerts_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.notificationsEnabled,
                    onCheckedChange = { checked ->
                        if (checked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            actions.toggleNotifications(checked)
                        }
                    },
                    modifier = Modifier.testTag(TAG_NOTIFICATIONS_SWITCH),
                )
            }

            if (uiState.notificationPermissionBlocked) {
                Text(
                    text = stringResource(R.string.notifications_blocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (settings.notificationsEnabled) {
                OutlinedButton(
                    onClick = {
                        val posted = actions.sendTestNotification()
                        if (!posted) {
                            scope.launch { snackbarHostState.showSnackbar(blockedMessage) }
                        }
                    },
                    modifier = Modifier.testTag(TAG_TEST_NOTIFICATION),
                ) {
                    Text(stringResource(R.string.test_notification))
                }
            }

            SectionDivider()
            AboutRow(onClick = onOpenAbout)
        }
    }
}

@Composable
private fun GeocodeStatus(
    state: GeocodeState,
    onSelect: (com.weatherquips.app.domain.model.GeocodeResult) -> Unit,
) {
    when (state) {
        GeocodeState.Idle -> Unit
        GeocodeState.Loading -> Text(
            text = stringResource(R.string.geocode_searching),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_GEOCODE_STATUS),
        )
        is GeocodeState.Message -> Text(
            text = state.argument?.let { stringResource(state.messageRes, it) }
                ?: stringResource(state.messageRes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_GEOCODE_STATUS),
        )
        is GeocodeState.Choices -> Column(modifier = Modifier.testTag(TAG_GEOCODE_CHOICES)) {
            state.results.forEach { result ->
                TextButton(onClick = { onSelect(result) }) {
                    Text(
                        text = "${result.name}  ·  %.2f, %.2f".format(result.latitude, result.longitude),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp)
            .testTag(TAG_ABOUT_ROW),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.about_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.about_row_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
private fun SectionTitle(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text = text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    testTag: String,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
                .testTag(testTag),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

const val TAG_SETTINGS_SCREEN = "settings-screen"
const val TAG_LANGUAGE_DROPDOWN = "settings-language"
const val TAG_ABOUT_ROW = "settings-about"
const val TAG_SETTINGS_BACK = "settings-back"
const val TAG_UNIT_DROPDOWN = "settings-unit"
const val TAG_TIME_DROPDOWN = "settings-time"
const val TAG_DATE_DROPDOWN = "settings-date"
const val TAG_LOCATION_MODE_DROPDOWN = "settings-location-mode"
const val TAG_CITY_FIELD = "settings-city"
const val TAG_FIND_BUTTON = "settings-find"
const val TAG_SERVICE_DROPDOWN = "settings-service"
const val TAG_API_KEY_FIELD = "settings-api-key"
const val TAG_NOTIFICATIONS_SWITCH = "settings-notifications"
const val TAG_TEST_NOTIFICATION = "settings-test-notification"
const val TAG_GEOCODE_STATUS = "settings-geocode-status"
const val TAG_GEOCODE_CHOICES = "settings-geocode-choices"
const val TAG_EXIT_POKEMON = "settings-exit-pokemon"
