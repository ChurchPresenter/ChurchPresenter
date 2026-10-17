package org.churchpresenter.calendar.sync

// The calls that add, remove and promote the other computers on an instance -- the main
// computer's, and a follower's own takeover. Beside [RelayClient] rather than in it, which already
// holds the protocol's every other call.

/** Adds a follower or viewer computer; main computer only. */
fun RelayClient.addComputer(token: String, computerId: String, request: AddComputerRequest) {
    require(Sanitize.isId(computerId)) { "computer id" }
    val body = json.encodeToString(AddComputerRequest.serializer(), request)
    call("PUT", "$base/desktops/$computerId", token, body)
}

fun RelayClient.removeComputer(token: String, computerId: String) {
    require(Sanitize.isId(computerId)) { "computer id" }
    call("DELETE", "$base/desktops/$computerId", token, body = null)
}

/** Makes [computerId] the main computer; see [PromoteRequest]. */
fun RelayClient.promote(token: String, computerId: String, request: PromoteRequest) {
    require(Sanitize.isId(computerId)) { "computer id" }
    val body = json.encodeToString(PromoteRequest.serializer(), request)
    call("POST", "$base/desktops/$computerId/promote", token, body)
}
