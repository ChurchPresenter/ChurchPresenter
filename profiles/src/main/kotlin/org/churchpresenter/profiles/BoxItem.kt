package org.churchpresenter.profiles

import org.churchpresenter.settings.TextBox

/**
 * One thing a single-form page draws that can be given a box: [key] is the item name its box is
 * stored under, [label] what the Item row calls it, and [start] where its box begins the first time
 * it is turned on.
 */
internal class BoxItem(val key: String, val label: String, val start: TextBox)
