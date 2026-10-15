package org.churchpresenter.web.presenter

/**
 * The system library a native load could not find -- `libnspr4.so` -- or null when [failure]
 * is not that.
 *
 * On Linux the engine links against libraries the distribution provides (NSS, NSPR, ALSA, GBM).
 * One that is not installed fails every load the same way whatever was extracted, so it is the
 * machine rather than the install: neither a fresh extraction nor another root can help. Only a
 * bare library name after `": "` counts -- a path there is one of the engine's own files, which
 * a fresh extraction can put back. Matched on `dlerror`'s English wording, so like [JcefInstall.policyBlock]
 * it only ever suppresses: a localised message is reported as before.
 */
internal fun missingSystemLibrary(failure: Throwable): String? {
    if (failure !is UnsatisfiedLinkError) return null
    return MISSING_LIBRARY.find(failure.message.orEmpty())?.groupValues?.get(1)
}

private val MISSING_LIBRARY = Regex("""(?<=: )(lib[^\s/:]+\.so[.\d]*): cannot open shared object file""")
