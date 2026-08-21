package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_archive
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_convert
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_generic
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_incomplete
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_network
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_stalled
import churchpresenter.composeapp.generated.resources.bible_catalog_download_error_write
import churchpresenter.composeapp.generated.resources.bible_catalog_error_generic
import churchpresenter.composeapp.generated.resources.bible_catalog_error_network
import churchpresenter.composeapp.generated.resources.bible_catalog_error_rate_limited
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_ebible
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_zefania
import churchpresenter.composeapp.generated.resources.bible_catalog_no_directory
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_converting
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_downloading
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_extracting
import churchpresenter.composeapp.generated.resources.bible_catalog_phase_installing
import churchpresenter.composeapp.generated.resources.bible_catalog_source_ebible
import churchpresenter.composeapp.generated.resources.bible_catalog_source_zefania
import churchpresenter.composeapp.generated.resources.bible_catalog_license_source_beblia
import churchpresenter.composeapp.generated.resources.bible_catalog_source_beblia
import org.churchpresenter.bibleformats.catalog.BibleSourceId
import org.churchpresenter.bibleformats.catalog.InstallPhase
import org.churchpresenter.app.churchpresenter.viewmodel.BibleCatalogError
import org.churchpresenter.app.churchpresenter.viewmodel.BibleDownloadError
import org.jetbrains.compose.resources.StringResource

internal fun phaseStringRes(phase: InstallPhase?): StringResource = when (phase) {
    InstallPhase.EXTRACTING -> Res.string.bible_catalog_phase_extracting
    InstallPhase.CONVERTING -> Res.string.bible_catalog_phase_converting
    InstallPhase.INSTALLING -> Res.string.bible_catalog_phase_installing
    else -> Res.string.bible_catalog_phase_downloading
}

internal fun sourceLabelStringRes(sourceId: BibleSourceId): StringResource = when (sourceId) {
    BibleSourceId.EBIBLE -> Res.string.bible_catalog_source_ebible
    BibleSourceId.ZEFANIA -> Res.string.bible_catalog_source_zefania
    BibleSourceId.BEBLIA -> Res.string.bible_catalog_source_beblia
}

internal fun sourceLicenceStringRes(sourceId: BibleSourceId): StringResource = when (sourceId) {
    BibleSourceId.EBIBLE -> Res.string.bible_catalog_license_source_ebible
    BibleSourceId.ZEFANIA -> Res.string.bible_catalog_license_source_zefania
    BibleSourceId.BEBLIA -> Res.string.bible_catalog_license_source_beblia
}

internal fun catalogErrorStringRes(error: BibleCatalogError): StringResource = when (error) {
    BibleCatalogError.NETWORK_ERROR -> Res.string.bible_catalog_error_network
    BibleCatalogError.RATE_LIMITED -> Res.string.bible_catalog_error_rate_limited
    BibleCatalogError.FAILURE -> Res.string.bible_catalog_error_generic
}

internal fun installErrorStringRes(error: BibleDownloadError): StringResource = when (error) {
    BibleDownloadError.NETWORK_ERROR -> Res.string.bible_catalog_download_error_network
    BibleDownloadError.DOWNLOAD_STALLED -> Res.string.bible_catalog_download_error_stalled
    BibleDownloadError.HTTP_ERROR -> Res.string.bible_catalog_download_error_generic
    BibleDownloadError.CHECKSUM_MISMATCH -> Res.string.bible_catalog_download_error_incomplete
    BibleDownloadError.CORRUPT_ARCHIVE -> Res.string.bible_catalog_download_error_archive
    BibleDownloadError.CONVERSION_FAILED -> Res.string.bible_catalog_download_error_convert
    BibleDownloadError.WRITE_FAILED -> Res.string.bible_catalog_download_error_write
    BibleDownloadError.NO_DIRECTORY -> Res.string.bible_catalog_no_directory
}
