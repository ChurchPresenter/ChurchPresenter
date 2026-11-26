package org.churchpresenter.slides.tabs

import org.churchpresenter.strings.generated.resources.pictures_cant_open_choose
import org.churchpresenter.strings.generated.resources.pictures_cant_open_retry
import org.churchpresenter.strings.generated.resources.pictures_cant_open_body
import org.churchpresenter.strings.generated.resources.pictures_cant_open_title
import org.churchpresenter.icons.generated.resources.ic_refresh
import org.churchpresenter.icons.generated.resources.ic_warning_outline
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import org.churchpresenter.sharedui.composables.EmptyStateSecondaryButton
import org.churchpresenter.sharedui.composables.EmptyStateButton
import org.churchpresenter.sharedui.composables.EmptyStateContent
import org.churchpresenter.sharedui.composables.EmptyStatePanel
import org.churchpresenter.sharedui.composables.FileDrop
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.churchpresenter.icons.generated.resources.ic_download
import org.churchpresenter.icons.generated.resources.ic_folder
import org.churchpresenter.icons.generated.resources.ic_storage_pictures
import org.churchpresenter.slides.viewmodel.PicturesViewModel
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.no_folder_selected
import org.churchpresenter.strings.generated.resources.pictures_drop_body
import org.churchpresenter.strings.generated.resources.pictures_drop_title
import org.churchpresenter.strings.generated.resources.pictures_empty_body
import org.churchpresenter.strings.generated.resources.pictures_empty_choose_another
import org.churchpresenter.strings.generated.resources.pictures_empty_choose_folder
import org.churchpresenter.strings.generated.resources.pictures_empty_drag_hint
import org.churchpresenter.strings.generated.resources.pictures_empty_folder_body
import org.churchpresenter.strings.generated.resources.pictures_empty_folder_title
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

/**
 * What the Pictures tab shows with no pictures: no folder chosen yet, or a folder with none in it.
 * The whole panel takes a dropped folder or picture.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PicturesTabScope.PicturesEmptyState(viewModel: PicturesViewModel) {
    val drop = remember(viewModel, this) { FileDrop { files -> openDropped(viewModel, files) } }
    EmptyStatePanel(drop, Modifier.fillMaxSize()) {
        val folder = viewModel.selectedFolder
        when {
            drop.dragOver -> EmptyStateContent(
                icon = IconRes.drawable.ic_download,
                neutral = false,
                title = stringResource(Res.string.pictures_drop_title),
                body = stringResource(Res.string.pictures_drop_body),
            )
            folder == null -> EmptyStateContent(
                icon = IconRes.drawable.ic_storage_pictures,
                neutral = false,
                title = stringResource(Res.string.no_folder_selected),
                body = stringResource(Res.string.pictures_empty_body),
                hint = stringResource(Res.string.pictures_empty_drag_hint),
            ) {
                EmptyStateButton(stringResource(Res.string.pictures_empty_choose_folder), IconRes.drawable.ic_folder) {
                    chooseFolder(viewModel)
                }
            }
            viewModel.folderUnreadable -> EmptyStateContent(
                icon = IconRes.drawable.ic_warning_outline,
                neutral = false,
                error = true,
                title = stringResource(Res.string.pictures_cant_open_title),
                body = stringResource(Res.string.pictures_cant_open_body, folder.name),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EmptyStateButton(stringResource(Res.string.pictures_cant_open_retry), IconRes.drawable.ic_refresh) {
                        viewModel.retryFolder()
                    }
                    EmptyStateSecondaryButton(
                        stringResource(Res.string.pictures_cant_open_choose),
                        IconRes.drawable.ic_folder,
                    ) { chooseFolder(viewModel) }
                }
            }
            else -> EmptyStateContent(
                icon = IconRes.drawable.ic_folder,
                neutral = true,
                title = stringResource(Res.string.pictures_empty_folder_title, folder.name),
                body = stringResource(Res.string.pictures_empty_folder_body),
            ) {
                EmptyStateButton(stringResource(Res.string.pictures_empty_choose_another), IconRes.drawable.ic_folder) {
                    chooseFolder(viewModel)
                }
            }
        }
    }
}
