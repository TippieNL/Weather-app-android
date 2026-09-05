package com.weatherquips.app.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.weatherquips.app.R
import com.weatherquips.app.ui.components.rememberAnimationsEnabled
import com.weatherquips.app.ui.theme.QuipHeadlineStyle

/** Skeleton placeholder while the first load is in flight. */
@Composable
fun LoadingScreen(modifier: Modifier = Modifier) {
    val animationsEnabled = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (animationsEnabled) 0.7f else 0.35f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "skeleton-alpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag(TAG_LOADING),
        verticalArrangement = Arrangement.Bottom,
    ) {
        SkeletonBlock(Modifier.size(128.dp), alpha)
        Spacer(Modifier.height(40.dp))
        SkeletonBlock(Modifier.fillMaxWidth().height(72.dp), alpha)
        Spacer(Modifier.height(16.dp))
        SkeletonBlock(Modifier.fillMaxWidth(0.66f).height(24.dp), alpha)
        Spacer(Modifier.height(56.dp))
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier, alpha: Float) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .graphicsLayer { this.alpha = alpha }
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

/** "Where are you?" — the location-permission state. */
@Composable
fun LocationPermissionScreen(
    deniedOnce: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateScaffold(
        iconRes = R.drawable.ic_map_pin,
        title = stringResource(R.string.permission_title),
        message = if (deniedOnce) {
            stringResource(R.string.error_location_denied)
        } else {
            stringResource(R.string.permission_description)
        },
        primaryLabel = stringResource(R.string.allow_location),
        primaryIconRes = R.drawable.ic_map_pin,
        onPrimary = onAllow,
        secondaryLabel = stringResource(R.string.use_manual_location),
        onSecondary = onOpenSettings,
        modifier = modifier.testTag(TAG_PERMISSION),
    )
}

/** "Oops…" — errors and location problems share this state. */
@Composable
fun MessageScreen(
    message: String,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StateScaffold(
        iconRes = R.drawable.ic_cloud_off,
        title = stringResource(R.string.error_title),
        message = message,
        primaryLabel = stringResource(R.string.try_again),
        primaryIconRes = R.drawable.ic_refresh,
        onPrimary = onRetry,
        secondaryLabel = stringResource(R.string.settings),
        onSecondary = onOpenSettings,
        modifier = modifier.testTag(TAG_ERROR),
    )
}

@Composable
private fun StateScaffold(
    iconRes: Int,
    title: String,
    message: String,
    primaryLabel: String,
    primaryIconRes: Int,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 32.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(40.dp))
        Text(
            text = title,
            style = QuipHeadlineStyle.copy(color = MaterialTheme.colorScheme.onBackground),
            modifier = Modifier.testTag(TAG_STATE_TITLE),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_STATE_MESSAGE),
        )
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPrimary, modifier = Modifier.testTag(TAG_STATE_PRIMARY)) {
                Icon(
                    painter = painterResource(primaryIconRes),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(primaryLabel, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onSecondary, modifier = Modifier.testTag(TAG_STATE_SECONDARY)) {
                Text(secondaryLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

const val TAG_LOADING = "home-loading"
const val TAG_PERMISSION = "home-permission"
const val TAG_ERROR = "home-error"
const val TAG_STATE_TITLE = "state-title"
const val TAG_STATE_MESSAGE = "state-message"
const val TAG_STATE_PRIMARY = "state-primary"
const val TAG_STATE_SECONDARY = "state-secondary"
