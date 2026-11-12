package org.churchpresenter.settings

import kotlinx.serialization.Serializable

/**
 * Which layout of a website an output shows: whatever the Web tab's Desktop/Mobile button says, or
 * always one of the two. A desktop page is landscape and a mobile page portrait; a screen of the
 * other shape draws it fitted.
 *
 * While two outputs want different layouts, each layout is its own page, loaded side by side: they
 * share the address, but scrolling and typing on one do not reach the other.
 */
@Serializable
enum class WebPageView {
    FOLLOW_WEB_TAB,
    MOBILE,
    DESKTOP,
    ;

    /** Whether this output shows the mobile page while the Web tab's button says [webTabMobile]. */
    fun isMobile(webTabMobile: Boolean): Boolean = when (this) {
        FOLLOW_WEB_TAB -> webTabMobile
        MOBILE -> true
        DESKTOP -> false
    }
}
