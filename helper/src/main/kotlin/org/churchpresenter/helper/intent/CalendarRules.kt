package org.churchpresenter.helper.intent

import org.churchpresenter.helper.action.CalendarTopic
import org.churchpresenter.helper.action.HelperAction

/**
 * Planning services ahead: "set up the service calendar", "make Sunday a recurring service", "save
 * a service template", "load the service into the schedule", "automate the service". Each opens the
 * Calendar Manager, saying how to do the part asked about. Today's Schedule tab is the
 * navigation rules' — "plan the service", "order of service".
 */
internal fun calendarRule(r: Request): Resolution? {
    val aboutService = r.has(Vocabulary.SERVICE)
    val topic = when {
        aboutService && r.hasPhrase(Vocabulary.RECURRING) -> CalendarTopic.REPEAT
        aboutService && r.hasPhrase(Vocabulary.TEMPLATE) -> CalendarTopic.TEMPLATE
        r.hasPhrase(Vocabulary.LOAD_SERVICE) -> CalendarTopic.LOAD
        aboutService && r.hasPhrase(Vocabulary.AUTOMATE) -> CalendarTopic.AUTOMATE
        r.hasPhrase(Vocabulary.CALENDAR) -> CalendarTopic.PLAN
        else -> return null
    }
    return act(HelperAction.OpenCalendar(topic))
}
