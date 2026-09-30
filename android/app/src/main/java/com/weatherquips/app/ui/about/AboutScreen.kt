package com.weatherquips.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.weatherquips.app.BuildConfig
import com.weatherquips.app.R
import com.weatherquips.app.ui.components.QuipCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What the app is, who made it, and whose work it stands on.
 *
 * The services and libraries come from [Credits], which tests check against
 * the network code and the build, so this page cannot quietly drift from
 * what the app actually does.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenLicence: (LicenceText) -> Unit,
    modifier: Modifier = Modifier,
    versionName: String = BuildConfig.VERSION_NAME,
    versionCode: Int = BuildConfig.VERSION_CODE,
) {
    val uriHandler = LocalUriHandler.current
    // No browser is not a reason to crash the About page.
    val open: (String) -> Unit = { url -> runCatching { uriHandler.openUri(url) } }

    PageScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag(TAG_ABOUT_SCREEN),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AppHeader(versionName = versionName, versionCode = versionCode)

            QuipCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    LinkRow(
                        title = stringResource(R.string.about_made_by),
                        detail = Credits.DEVELOPER,
                        onClick = { open(Credits.SOURCE_URL) },
                    )
                    LinkRow(
                        title = stringResource(R.string.about_source_code),
                        detail = Credits.SOURCE_URL.removePrefix("https://"),
                        onClick = { open(Credits.SOURCE_URL) },
                    )
                    InfoRow(
                        title = stringResource(R.string.about_contributors),
                        detail = stringResource(R.string.about_contributors_detail, Credits.DEVELOPER),
                    )
                }
            }

            Section(stringResource(R.string.about_services))
            Text(
                text = stringResource(R.string.about_privacy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            QuipCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Credits.services.forEachIndexed { index, service ->
                        if (index > 0) RowDivider()
                        ServiceRow(service, onClick = { open(service.url) })
                    }
                    RowDivider()
                    InfoRow(
                        title = stringResource(R.string.about_service_location_name),
                        detail = stringResource(R.string.about_service_location),
                    )
                }
            }

            Section(stringResource(R.string.about_libraries))
            QuipCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Credits.libraries.forEachIndexed { index, library ->
                        if (index > 0) RowDivider()
                        LibraryRow(library, onOpenLicence = { onOpenLicence(library.licence) })
                    }
                }
            }

            Section(stringResource(R.string.about_fonts_icons))
            QuipCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    Credits.fontsAndIcons.forEachIndexed { index, credit ->
                        if (index > 0) RowDivider()
                        LibraryRow(credit, onOpenLicence = { onOpenLicence(credit.licence) })
                    }
                }
            }

            Section(stringResource(R.string.about_trademarks))
            Text(
                text = stringResource(R.string.about_pokemon_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A bundled licence, in full, as the licence requires. */
@Composable
fun LicenceScreen(
    licence: LicenceText,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var text by remember(licence) { mutableStateOf<String?>(null) }
    LaunchedEffect(licence) {
        text = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(licence.asset).bufferedReader().use { it.readText() }
            }.getOrNull()
        }
    }

    PageScaffold(title = licence.title, onBack = onBack, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag(TAG_LICENCE_SCREEN),
        ) {
            val body = text
            when {
                body != null -> SelectionContainer {
                    // Licences are laid out for fixed-width type; reflowing
                    // them breaks the indented clauses. Scroll sideways instead.
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            lineHeight = 15.sp,
                        ),
                        softWrap = false,
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .testTag(TAG_LICENCE_TEXT),
                    )
                }
                // Still loading: the read is quick, so no spinner to flash.
                else -> Unit
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PageScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag(TAG_ABOUT_BACK)) {
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
        Box(Modifier.padding(innerPadding)) { content() }
    }
}

@Composable
private fun AppHeader(versionName: String, versionCode: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(LauncherBackground),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(72.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.about_version, versionName, versionCode),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(TAG_ABOUT_VERSION),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.about_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier
            .padding(top = 8.dp)
            .semantics { heading() },
    )
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun ServiceRow(service: ServiceCredit, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.about_open_link, service.name),
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("$TAG_ABOUT_SERVICE_PREFIX${service.name}"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Name and tag share the row's slack; the chevron keeps the edge.
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(text = service.name, style = MaterialTheme.typography.titleSmall)
                if (service.needsApiKey) {
                    Spacer(Modifier.width(8.dp))
                    Tag(stringResource(R.string.about_needs_key))
                }
            }
            ChevronOut()
        }
        Text(
            text = stringResource(service.purpose),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
        Text(
            text = listOfNotNull(service.attribution, service.dataLicence).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun LibraryRow(library: LibraryCredit, onOpenLicence: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.about_read_licence),
                onClick = onOpenLicence,
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("$TAG_ABOUT_LIBRARY_PREFIX${library.name}"),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = library.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = library.copyright,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = library.licence.title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Icon(
            painter = painterResource(R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun LinkRow(title: String, detail: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = stringResource(R.string.about_open_link, detail), onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = detail, style = MaterialTheme.typography.titleSmall)
        }
        ChevronOut()
    }
}

@Composable
private fun InfoRow(title: String, detail: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Tag(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun ChevronOut() {
    Icon(
        painter = painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}

/** The adaptive icon's background layer, which painterResource cannot load as a mipmap. */
private val LauncherBackground = Color(0xFF0A0A0A)

const val TAG_ABOUT_SCREEN = "about-screen"
const val TAG_ABOUT_BACK = "about-back"
const val TAG_ABOUT_VERSION = "about-version"
const val TAG_ABOUT_SERVICE_PREFIX = "about-service-"
const val TAG_ABOUT_LIBRARY_PREFIX = "about-library-"
const val TAG_LICENCE_SCREEN = "licence-screen"
const val TAG_LICENCE_TEXT = "licence-text"
