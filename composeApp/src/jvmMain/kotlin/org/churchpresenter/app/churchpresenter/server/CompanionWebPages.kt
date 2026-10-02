package org.churchpresenter.app.churchpresenter.server

/**
 * The self-contained web pages the companion server serves to phones: the Q&A submission, voting
 * and moderation pages, and the presentation remote.
 *
 * The markup lives in `resources/companion/`, so a page is edited as HTML rather than as a Kotlin
 * string. The three Q&A pages share `qa-shared.css`, inlined where a page says `/*QA_SHARED_CSS*/`.
 */

private const val SHARED_CSS_MARKER = "/*QA_SHARED_CSS*/"

private object CompanionWebPages

private fun companionResource(name: String): String =
    checkNotNull(CompanionWebPages::class.java.getResource("/companion/$name")) { "missing companion/$name" }
        .readText()
        .removeSuffix("\n")

private val qaSharedCss by lazy { companionResource("qa-shared.css") }

private fun qaPage(name: String): String = companionResource(name).replace(SHARED_CSS_MARKER, qaSharedCss)

private val qaSubmissionPage by lazy { qaPage("qa-submission.html") }
private val qaVotingPage by lazy { qaPage("qa-voting.html") }
private val qaAdminPage by lazy { qaPage("qa-admin.html") }
private val presentationRemotePage by lazy { companionResource("presentation-remote.html") }

internal fun qaSubmissionPageHtml(): String = qaSubmissionPage

internal fun qaVotingPageHtml(): String = qaVotingPage

internal fun qaAdminPageHtml(): String = qaAdminPage

internal fun presentationRemotePageHtml(): String = presentationRemotePage
