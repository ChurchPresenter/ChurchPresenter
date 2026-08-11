package org.churchpresenter.app.churchpresenter.data

import org.churchpresenter.app.churchpresenter.utils.Constants
import java.io.File

/** The archives the downloader can install from, in the order they are offered. */
enum class BibleSourceId { EBIBLE, ZEFANIA }

/** Which portion of scripture a translation covers. */
enum class Testament { OLD, NEW, FULL }

private val NT_TOKEN = Regex("\\bNT\\b")
private val OT_TOKEN = Regex("\\bOT\\b")

/**
 * One downloadable translation, as shown in the browse list.
 *
 * Everything here comes from the source's catalogue, without downloading the translation itself —
 * which is what lets the list show sizes and mark what is already installed while staying at one
 * network request per source.
 */
data class BibleModule(
    val sourceId: BibleSourceId,
    /** How the source locates the download: a repository path, or a translation id. */
    val downloadKey: String,
    /** Verifies the bytes when the source publishes one; blank when it doesn't. */
    val checksum: String = "",
    val sizeBytes: Long = 0,
    val language: String,
    /**
     * English name for [language], when the source publishes one; blank when it doesn't.
     *
     * Only eBible carries this. The Zefania archive names its language folders by code alone, so
     * that path resolves the name through [BibleLanguageNames] instead.
     */
    val languageName: String = "",
    /**
     * What the language calls itself, when the source publishes it; blank when it doesn't.
     *
     * Kept alongside [languageName] rather than instead of it: the two agree for about two thirds of
     * eBible's rows, and where they differ the autonym is the only spelling a speaker of that
     * language would think to type.
     */
    val languageNativeName: String = "",
    val identifier: String,
    val displayName: String,
    /** Blank when the source only reveals it inside the file, as the Zefania archive does. */
    val copyright: String = "",
    val releaseDate: String = "",
    /**
     * How many books of each testament the translation contains, where the source publishes counts;
     * both zero when it doesn't, which is the Zefania archive's case.
     */
    val otBookCount: Int = 0,
    val ntBookCount: Int = 0,
    val fileStem: String
) {
    val fileName: String get() = "$fileStem.${Constants.EXTENSION_SPB}"

    /** Stable across sources, so an install in one tab can't be confused with one in another. */
    val key: String get() = "${sourceId.name}:$downloadKey"

    /**
     * Which portion of scripture this covers.
     *
     * Taken from [otBookCount]/[ntBookCount] where the source publishes them, since that is the
     * translation's actual contents rather than a reading of its title. Only where it doesn't is
     * the name consulted: a standalone "NT" or "OT" token names the portion, and a name with
     * neither is assumed to be a whole Bible.
     *
     * The fallback is a guess and reads like one — it takes "New Testament in Achi" for a complete
     * Bible, because that name spells the words out instead of using the token. Roughly a fifth of
     * eBible's catalogue is misread that way, which is why the published counts win wherever they
     * exist.
     */
    val testament: Testament
        get() {
            if (otBookCount > 0 || ntBookCount > 0) {
                return when {
                    otBookCount > 0 && ntBookCount > 0 -> Testament.FULL
                    ntBookCount > 0 -> Testament.NEW
                    else -> Testament.OLD
                }
            }
            val hasNt = NT_TOKEN.containsMatchIn(displayName)
            val hasOt = OT_TOKEN.containsMatchIn(displayName)
            return when {
                hasNt && !hasOt -> Testament.NEW
                hasOt && !hasNt -> Testament.OLD
                else -> Testament.FULL
            }
        }
}

sealed interface BibleCatalogOutcome {
    /** [stale] means this came from the on-disk copy because the source was unreachable. */
    data class Success(val modules: List<BibleModule>, val stale: Boolean = false) : BibleCatalogOutcome
    /** The host is refusing requests for now; [resetEpochSeconds] says until when, if known. */
    data class RateLimited(val resetEpochSeconds: Long?) : BibleCatalogOutcome
    data object NetworkError : BibleCatalogOutcome
    data object Failure : BibleCatalogOutcome
}

enum class InstallPhase { DOWNLOADING, EXTRACTING, CONVERTING, INSTALLING }

/** [fraction] spans the whole install, so the bar never resets between phases. */
data class InstallProgress(val phase: InstallPhase, val fraction: Float)

sealed interface BibleInstallOutcome {
    data class Success(val file: File, val title: String, val books: Int, val rights: String) : BibleInstallOutcome
    data class HttpError(val status: Int) : BibleInstallOutcome
    data object NetworkError : BibleInstallOutcome
    /**
     * Every attempt timed out part-way through the body. Distinct from [NetworkError]: the link
     * works, it just keeps stopping — so the answer is to try again, not to check the cable.
     */
    data object DownloadStalled : BibleInstallOutcome
    /** The bytes that arrived don't match the size or hash the catalogue listed. */
    data object ChecksumMismatch : BibleInstallOutcome
    /** Not an archive, or nothing usable inside it. */
    data object CorruptArchive : BibleInstallOutcome
    /** It parsed but produced no scripture. */
    data object ConversionFailed : BibleInstallOutcome
    data object WriteFailed : BibleInstallOutcome
    data object NoDirectory : BibleInstallOutcome
}

/**
 * An archive of downloadable Bibles.
 *
 * Each source owns both halves of the job — listing what it has, and turning one of its downloads
 * into an installed `.spb` — because the two are tightly coupled: eBible publishes USFX with a
 * separate book-name list, the Zefania archive publishes a single XML in a zip, and neither knows
 * anything about the other's format.
 */
interface BibleSource {
    val sourceId: BibleSourceId

    suspend fun catalog(nowMillis: Long = System.currentTimeMillis()): BibleCatalogOutcome

    suspend fun install(
        module: BibleModule,
        targetDir: File,
        onProgress: (InstallProgress) -> Unit = {},
    ): BibleInstallOutcome
}
