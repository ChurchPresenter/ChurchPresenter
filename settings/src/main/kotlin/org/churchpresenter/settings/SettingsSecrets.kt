package org.churchpresenter.settings

/**
 * The passwords, sign-ins and API keys settings hold, in one place: what an export for sharing
 * leaves out, and what an import keeps from this computer when asked to.
 *
 * - Planning Center: the sign-in tokens, and the name of the person they belong to.
 * - Calendar sync: this computer's pairing with the relay ([CalendarSyncSettings]).
 * - OBS: the WebSocket password.
 * - The remote-control server's API key, and the key an InstanceLink follower connects with.
 * - The Pexels and Pixabay API keys the stock-photo browser searches with.
 *
 * A setting added that is a credential belongs here, or it leaks into every shared export.
 */
fun AppSettings.withoutSecrets(): AppSettings = copy(
    planningCenterSettings = planningCenterSettings.copy(
        accessToken = "",
        refreshToken = "",
        tokenExpiresAtEpochMs = 0L,
        connectedPersonName = "",
    ),
    calendarSync = CalendarSyncSettings(),
    obsSettings = obsSettings.copy(password = ""),
    serverSettings = serverSettings.copy(apiKey = ""),
    instanceLink = instanceLink.copy(apiKey = ""),
    stockPhotoSettings = stockPhotoSettings.copy(pexelsApiKey = "", pixabayApiKey = ""),
)

/**
 * True when these settings carry a password, sign-in or API key of their own. Calendar sync is left
 * out: it is never exported, and an import always keeps this computer's.
 */
val AppSettings.hasSecrets: Boolean
    get() = listOf(
        planningCenterSettings.accessToken,
        planningCenterSettings.refreshToken,
        obsSettings.password,
        serverSettings.apiKey,
        instanceLink.apiKey,
        stockPhotoSettings.pexelsApiKey,
        stockPhotoSettings.pixabayApiKey,
    ).any { it.isNotBlank() }

/** These settings with every password, sign-in and API key taken from [source] instead. */
fun AppSettings.withSecretsFrom(source: AppSettings): AppSettings = copy(
    planningCenterSettings = planningCenterSettings.copy(
        accessToken = source.planningCenterSettings.accessToken,
        refreshToken = source.planningCenterSettings.refreshToken,
        tokenExpiresAtEpochMs = source.planningCenterSettings.tokenExpiresAtEpochMs,
        connectedPersonName = source.planningCenterSettings.connectedPersonName,
    ),
    calendarSync = source.calendarSync,
    obsSettings = obsSettings.copy(password = source.obsSettings.password),
    serverSettings = serverSettings.copy(apiKey = source.serverSettings.apiKey),
    instanceLink = instanceLink.copy(apiKey = source.instanceLink.apiKey),
    stockPhotoSettings = stockPhotoSettings.copy(
        pexelsApiKey = source.stockPhotoSettings.pexelsApiKey,
        pixabayApiKey = source.stockPhotoSettings.pixabayApiKey,
    ),
)

/**
 * The settings an import saves: [imported], with this computer's ([current]) passwords, sign-ins
 * and API keys unless [useFileSecrets]. A file that carries none keeps this computer's whatever is
 * asked, and calendar sync is always this computer's -- adopting another's pairing would make two
 * desktops answer as one.
 */
fun importedSettings(imported: AppSettings, current: AppSettings, useFileSecrets: Boolean): AppSettings =
    if (useFileSecrets && imported.hasSecrets) {
        imported.copy(calendarSync = current.calendarSync)
    } else {
        imported.withSecretsFrom(current)
    }
