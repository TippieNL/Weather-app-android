package com.weatherquips.app.ui.onboarding

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherquips.app.R
import com.weatherquips.app.ui.components.QuoteText
import com.weatherquips.app.ui.components.plainQuote
import com.weatherquips.app.ui.theme.QuipHeadlineStyle
import kotlinx.coroutines.launch

/**
 * First run, in the app's own voice.
 *
 * The last page explains what location is for *before* the system dialog
 * appears, so the choice is an informed one rather than a prompt out of
 * nowhere — and declining is a first-class option, not a dead end.
 */
@Composable
fun OnboardingScreen(
    uiState: OnboardingUiState,
    onLocationResult: (Boolean) -> Unit,
    onFinish: (chooseManualLocation: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = remember { onboardingPages() }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> onLocationResult(grants.values.any { it }) }

    // Granting is answer enough — no need to make them tap "next" as well.
    LaunchedEffect(uiState.locationChoice) {
        if (uiState.locationChoice == LocationChoice.GRANTED) onFinish(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(TAG_ONBOARDING),
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) { index ->
            OnboardingPageContent(
                page = pages[index],
                locationChoice = uiState.locationChoice,
            )
        }

        if (!isLastPage) {
            TextButton(
                onClick = { onFinish(false) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(8.dp)
                    .testTag(TAG_SKIP),
            ) {
                Text(stringResource(R.string.onboarding_skip))
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            PageIndicator(
                pageCount = pages.size,
                currentPage = pagerState.currentPage,
                modifier = Modifier.padding(bottom = 20.dp),
            )

            if (isLastPage) {
                Button(
                    onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                // Asking for both lets the system offer the
                                // precise/approximate choice on Android 12+.
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION,
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_ALLOW_LOCATION),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_location_allow),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { onFinish(true) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_MANUAL_LOCATION),
                ) {
                    Text(stringResource(R.string.onboarding_location_manual))
                }
            } else {
                Button(
                    onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TAG_NEXT),
                ) {
                    Text(
                        text = stringResource(R.string.onboarding_next),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage, locationChoice: LocationChoice) {
    val title = stringResource(page.titleRes)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            // The location page carries a second button, so it needs more room
            // reserved at the bottom than the others.
            .padding(top = 48.dp, bottom = if (page.isLocationPage) 200.dp else 164.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(
            painter = painterResource(page.iconRes),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(36.dp))

        QuoteText(
            quote = title,
            // Above 15 °C the highlight is the hot accent; these pages are not
            // about a temperature, so the warm one simply reads better here.
            temperatureCelsius = 20.0,
            style = QuipHeadlineStyle.copy(
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 36.sp,
                lineHeight = 40.sp,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(page.testTag)
                .clearAndSetSemantics { contentDescription = plainQuote(title) },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(page.bodyRes),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (page.isLocationPage) {
            AnimatedVisibility(
                visible = locationChoice != LocationChoice.UNANSWERED,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Text(
                    text = stringResource(
                        if (locationChoice == LocationChoice.GRANTED) {
                            R.string.onboarding_location_granted
                        } else {
                            R.string.onboarding_location_denied
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .testTag(TAG_LOCATION_RESULT),
                )
            }
        }
    }
}

@Composable
private fun PageIndicator(pageCount: Int, currentPage: Int, modifier: Modifier = Modifier) {
    val description = stringResource(R.string.onboarding_page_indicator, currentPage + 1, pageCount)
    Row(
        modifier = modifier.clearAndSetSemantics { contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(pageCount) { index ->
            val selected = index == currentPage
            val width by animateDpAsState(if (selected) 20.dp else 6.dp, label = "indicator")
            Box(
                modifier = Modifier
                    .height(6.dp)
                    .width(width)
                    .clip(CircleShape)
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                        },
                    ),
            )
        }
    }
}

private data class OnboardingPage(
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
    val testTag: String,
    val isLocationPage: Boolean = false,
)

private fun onboardingPages() = listOf(
    OnboardingPage(
        iconRes = R.drawable.ic_weather_cloudy_night,
        titleRes = R.string.onboarding_welcome_title,
        bodyRes = R.string.onboarding_welcome_body,
        testTag = TAG_PAGE_WELCOME,
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_weather_stormy,
        titleRes = R.string.onboarding_quips_title,
        bodyRes = R.string.onboarding_quips_body,
        testTag = TAG_PAGE_QUIPS,
    ),
    OnboardingPage(
        iconRes = R.drawable.ic_map_pin,
        titleRes = R.string.onboarding_location_title,
        bodyRes = R.string.onboarding_location_body,
        testTag = TAG_PAGE_LOCATION,
        isLocationPage = true,
    ),
)

const val TAG_ONBOARDING = "onboarding"
const val TAG_SKIP = "onboarding-skip"
const val TAG_NEXT = "onboarding-next"
const val TAG_ALLOW_LOCATION = "onboarding-allow-location"
const val TAG_MANUAL_LOCATION = "onboarding-manual-location"
const val TAG_LOCATION_RESULT = "onboarding-location-result"
const val TAG_PAGE_WELCOME = "onboarding-page-welcome"
const val TAG_PAGE_QUIPS = "onboarding-page-quips"
const val TAG_PAGE_LOCATION = "onboarding-page-location"
