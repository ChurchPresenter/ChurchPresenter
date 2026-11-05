package org.churchpresenter.signage.tab

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import org.churchpresenter.theme.semantic
import org.churchpresenter.strings.generated.resources.signage_smugmug_subtitle
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.churchpresenter.sharedui.composables.LabeledCheckbox
import org.churchpresenter.sharedui.composables.NumberSettingsTextField
import org.churchpresenter.sharedui.composables.SettingsScrollbar
import org.churchpresenter.sharedui.composables.SettingsScrollbarGutter
import org.churchpresenter.signage.smugmug.SmugMugAlbum
import org.churchpresenter.signage.smugmug.SmugMugPick
import org.churchpresenter.signage.smugmug.SmugMugSite
import org.churchpresenter.signage.smugmug.SmugMugStatusException
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.save
import org.churchpresenter.strings.generated.resources.signage_smugmug_galleries
import org.churchpresenter.strings.generated.resources.signage_smugmug_galleries_hint
import org.churchpresenter.strings.generated.resources.signage_smugmug_get_key
import org.churchpresenter.strings.generated.resources.signage_smugmug_hours
import org.churchpresenter.strings.generated.resources.signage_smugmug_key
import org.churchpresenter.strings.generated.resources.signage_smugmug_key_hint
import org.churchpresenter.strings.generated.resources.signage_smugmug_looking
import org.churchpresenter.strings.generated.resources.signage_smugmug_name
import org.churchpresenter.strings.generated.resources.signage_smugmug_found
import org.churchpresenter.strings.generated.resources.signage_smugmug_not_found
import org.churchpresenter.strings.generated.resources.signage_smugmug_photos
import org.churchpresenter.strings.generated.resources.signage_smugmug_seconds
import org.churchpresenter.strings.generated.resources.signage_smugmug_site
import org.churchpresenter.strings.generated.resources.signage_smugmug_site_hint
import org.churchpresenter.strings.generated.resources.signage_smugmug_test
import org.churchpresenter.strings.generated.resources.signage_smugmug_title
import org.churchpresenter.strings.generated.resources.signage_smugmug_title_edit
import org.churchpresenter.strings.generated.resources.signage_smugmug_unlisted
import org.churchpresenter.strings.generated.resources.signage_smugmug_unlisted_hint
import org.churchpresenter.strings.generated.resources.signage_smugmug_unreachable
import org.churchpresenter.strings.generated.resources.signage_smugmug_wrong_key
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.components.SettingsTextField
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource
import java.io.IOException

/** Looks a site up for the dialog, with what it found (or why not) in [result]. */
private class SiteLookup(private val lookUp: suspend (site: String, key: String) -> SmugMugSite) {
    val result = mutableStateOf<Lookup>(Lookup.NotYet)

    suspend fun run(site: String, key: String) {
        result.value = Lookup.Looking
        result.value = try {
            Lookup.Found(lookUp(site, key))
        } catch (e: SmugMugStatusException) {
            Lookup.Failed(e.status, e.message.orEmpty())
        } catch (e: IOException) {
            Lookup.Failed(null, e.message.orEmpty())
        }
    }
}

/** What the dialog found when it looked the site up. */
private sealed interface Lookup {
    data object NotYet : Lookup

    data object Looking : Lookup

    data class Found(val site: SmugMugSite) : Lookup

    /** [status] is SmugMug's answer when it gave one: 401 for the key, 404 for the site. */
    data class Failed(val status: Int?, val message: String) : Lookup
}

/**
 * Adds or edits a SmugMug slideshow: the site, how many photos and for how long each, how often a new
 * set is picked, and which galleries it takes from once the site has been looked up (Test). The API
 * key it reads with is this computer's, kept with the app's other service keys ([apiKey], saved
 * through [onApiKey]).
 */
@Composable
internal fun SmugMugDialog(
    initial: SmugMugPlaylist?,
    apiKey: String,
    onApiKey: (String) -> Unit,
    lookUp: suspend (site: String, key: String) -> SmugMugSite,
    onSave: (SmugMugPlaylist) -> Unit,
    onDismiss: () -> Unit,
    openUrl: (String) -> Unit,
) {
    val start = initial ?: SmugMugPlaylist.create("")
    var name by remember { mutableStateOf(start.name) }
    var site by remember { mutableStateOf(start.site) }
    var key by remember { mutableStateOf(apiKey) }
    var photos by remember { mutableIntStateOf(start.photoCount) }
    var seconds by remember { mutableIntStateOf(start.pictureSeconds) }
    var hours by remember { mutableIntStateOf(start.refreshHours) }
    var unlisted by remember { mutableStateOf(start.includeUnlisted) }
    var excluded by remember { mutableStateOf(start.excludedAlbumKeys) }
    val looked = remember { SiteLookup(lookUp) }
    var lookup by looked.result
    val scope = rememberCoroutineScope()
    val test: () -> Unit = { scope.launch { looked.run(site, key) } }
    // A slideshow opened to edit shows its galleries straight away.
    LaunchedEffect(Unit) { if (initial != null && site.isNotBlank() && key.isNotBlank()) test() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val title = if (initial == null) Res.string.signage_smugmug_title else Res.string.signage_smugmug_title_edit
            SignageDialogTitle(
                stringResource(title),
                stringResource(Res.string.signage_smugmug_subtitle),
                kind = TileKind.SMUGMUG,
            )
        },
        text = {
            val scroll = rememberScrollState()
            Box {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.width(DIALOG_WIDTH)
                        .verticalScroll(scroll)
                        .padding(end = SettingsScrollbarGutter),
                ) {
                    SiteFields(
                        site = site,
                        onSite = {
                            site = it
                            lookup = Lookup.NotYet
                        },
                        key = key,
                        onKey = { key = it },
                        onTest = test,
                        found = { LookupLine(lookup, unlisted, excluded) },
                        openUrl = openUrl,
                    )
                    SettingsTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(Res.string.signage_smugmug_name),
                        fillWidth = true,
                        modifier = Modifier.fillMaxWidth().testTag(SMUGMUG_NAME_TAG),
                    )
                    NumberFields(
                        photos = photos,
                        seconds = seconds,
                        hours = hours,
                        onPhotos = { photos = it },
                        onSeconds = { seconds = it },
                        onHours = { hours = it },
                    )
                    InfoSwitch(
                        title = stringResource(Res.string.signage_smugmug_unlisted),
                        info = stringResource(Res.string.signage_smugmug_unlisted_hint),
                        checked = unlisted,
                        onChange = { unlisted = it },
                    )
                    (lookup as? Lookup.Found)?.let { found ->
                        Galleries(found.site.albums, unlisted, excluded) { excluded = it }
                    }
                }
                Box(Modifier.matchParentSize()) { SettingsScrollbar(scroll) }
            }
        },
        confirmButton = {
            RaisedButton(
                onClick = {
                    if (key != apiKey) onApiKey(key.trim())
                    val found = (lookup as? Lookup.Found)?.site?.nickname
                    onSave(
                        start.copy(
                            name = name.trim().ifEmpty { found ?: site.trim() },
                            site = site.trim(),
                            photoCount = photos,
                            pictureSeconds = seconds,
                            refreshHours = hours,
                            includeUnlisted = unlisted,
                            excludedAlbumKeys = excluded,
                        ),
                    )
                    onDismiss()
                },
                enabled = site.isNotBlank(),
            ) { Text(stringResource(Res.string.save)) }
        },
        dismissButton = { RingedGhostButton(onClick = onDismiss) { Text(stringResource(Res.string.cancel)) } },
    )
}

/**
 * The site with its Test button, what the lookup [found] under it, and this computer's API key with
 * the way to get one.
 */
@Composable
private fun SiteFields(
    site: String,
    onSite: (String) -> Unit,
    key: String,
    onKey: (String) -> Unit,
    onTest: () -> Unit,
    found: @Composable () -> Unit,
    openUrl: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsTextField(
            value = site,
            onValueChange = onSite,
            label = stringResource(Res.string.signage_smugmug_site),
            fillWidth = true,
            modifier = Modifier.weight(1f).testTag(SMUGMUG_SITE_TAG),
        )
        InfoHint(stringResource(Res.string.signage_smugmug_site_hint))
        KeyButton(onClick = onTest, enabled = site.isNotBlank() && key.isNotBlank()) {
            Text(stringResource(Res.string.signage_smugmug_test))
        }
    }
    found()
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        SettingsTextField(
            value = key,
            onValueChange = onKey,
            label = stringResource(Res.string.signage_smugmug_key),
            visualTransformation = PasswordVisualTransformation(),
            fillWidth = true,
            modifier = Modifier.weight(1f).testTag(SMUGMUG_KEY_TAG),
        )
        InfoHint(stringResource(Res.string.signage_smugmug_key_hint))
        RingedGhostButton(onClick = { openUrl(API_KEY_URL) }) {
            Text(stringResource(Res.string.signage_smugmug_get_key))
        }
    }
}

/** How many photos, how long each stays up, and how often a new set is picked, in one row. */
@Composable
private fun NumberFields(
    photos: Int,
    seconds: Int,
    hours: Int,
    onPhotos: (Int) -> Unit,
    onSeconds: (Int) -> Unit,
    onHours: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_smugmug_photos),
            initialText = photos,
            range = SmugMugPlaylist.PHOTO_COUNTS,
            onValueChange = onPhotos,
            modifier = Modifier.weight(1f),
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_smugmug_seconds),
            initialText = seconds,
            range = PickedFolderPlaylist.PICTURE_SECONDS,
            onValueChange = onSeconds,
            modifier = Modifier.weight(1f),
        )
        NumberSettingsTextField(
            label = stringResource(Res.string.signage_smugmug_hours),
            initialText = hours,
            range = SmugMugPlaylist.REFRESH_HOURS,
            onValueChange = onHours,
            modifier = Modifier.weight(1f),
        )
    }
}

/** What the lookup found: the galleries and photos the slideshow would take from, or why it failed. */
@Composable
private fun LookupLine(lookup: Lookup, unlisted: Boolean, excluded: Set<String>) {
    val text = when (lookup) {
        Lookup.NotYet -> return
        Lookup.Looking -> stringResource(Res.string.signage_smugmug_looking)
        is Lookup.Found -> {
            val used = SmugMugPick.albumsToUse(lookup.site.albums, unlisted, excluded)
            stringResource(Res.string.signage_smugmug_found, used.size, used.sumOf { it.imageCount })
        }
        is Lookup.Failed -> when (lookup.status) {
            UNAUTHORIZED -> stringResource(Res.string.signage_smugmug_wrong_key)
            NOT_FOUND -> stringResource(Res.string.signage_smugmug_not_found)
            else -> stringResource(Res.string.signage_smugmug_unreachable, lookup.message)
        }
    }
    val colour = when (lookup) {
        is Lookup.Failed -> MaterialTheme.colorScheme.error
        is Lookup.Found -> MaterialTheme.semantic.success
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(colour.copy(alpha = RESULT_WASH), RoundedCornerShape(9.dp))
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val icon = when (lookup) {
            is Lookup.Found -> Icons.Filled.CheckCircle
            is Lookup.Failed -> Icons.Filled.Error
            else -> null
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colour, modifier = Modifier.size(16.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
        }
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = colour,
            modifier = Modifier.testTag(SMUGMUG_LOOKUP_TAG),
        )
    }
}

/** The galleries a slideshow can take from, each ticked unless it is left out. */
@Composable
private fun Galleries(
    albums: List<SmugMugAlbum>,
    unlisted: Boolean,
    excluded: Set<String>,
    onExcluded: (Set<String>) -> Unit,
) {
    val offered = SmugMugPick.albumsToUse(albums, unlisted, emptySet())
    if (offered.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FieldCaption(
            stringResource(Res.string.signage_smugmug_galleries),
            info = stringResource(Res.string.signage_smugmug_galleries_hint),
        )
        Column(
            Modifier.fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .heightIn(max = GALLERIES_HEIGHT).verticalScroll(rememberScrollState()),
        ) {
            offered.forEach { album ->
                LabeledCheckbox(
                    checked = album.key !in excluded,
                    onCheckedChange = { on -> onExcluded(if (on) excluded - album.key else excluded + album.key) },
                    label = "${album.name} (${album.imageCount})",
                )
            }
        }
    }
}

private const val RESULT_WASH = 0.12f

/** Where SmugMug hands out API keys. */
internal const val API_KEY_URL = "https://api.smugmug.com/api/developer/apply"

/** The dialog's text fields, whose captions are drawn apart from the box a test types into. */
internal const val SMUGMUG_SITE_TAG = "signage-smugmug-site"
internal const val SMUGMUG_KEY_TAG = "signage-smugmug-key"
internal const val SMUGMUG_NAME_TAG = "signage-smugmug-name"
internal const val SMUGMUG_LOOKUP_TAG = "signage-smugmug-lookup"
private const val UNAUTHORIZED = 401
private const val NOT_FOUND = 404
private val DIALOG_WIDTH = 520.dp
private val GALLERIES_HEIGHT = 220.dp
