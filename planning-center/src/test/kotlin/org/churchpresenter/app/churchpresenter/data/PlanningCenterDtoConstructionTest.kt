package org.churchpresenter.app.churchpresenter.data

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Every DTO below is `@Serializable` and `internal` to [PlanningCenterClient], so
 * kotlinx.serialization generates it a *second*, masked constructor purely for deserializing JSON.
 * Decoding a real response — which is all the rest of the suite ever does — only ever calls that
 * masked constructor, never the plain one a caller would use to build one by hand. Nothing builds
 * one by hand today, but the plain constructor and its accessors are still real, callable code, so
 * it is worth pinning that a value put in comes back out unchanged.
 */
class PlanningCenterDtoConstructionTest {

    @Test
    fun `token and person dtos round-trip their fields`() {
        val token = PlanningCenterClient.TokenResponse(
            accessToken = "tok",
            refreshToken = "ref",
            expiresIn = 7200L,
        )
        assertEquals("tok", token.accessToken)
        assertEquals("ref", token.refreshToken)
        assertEquals(7200L, token.expiresIn)

        val attrs = PlanningCenterClient.PersonAttributes(
            name = "Pat Ringer",
            firstName = "Pat",
            lastName = "Ringer",
        )
        assertEquals("Pat Ringer", attrs.name)
        assertEquals("Pat", attrs.firstName)
        assertEquals("Ringer", attrs.lastName)

        val data = PlanningCenterClient.PersonData(id = "1", attributes = attrs)
        assertEquals("1", data.id)
        assertEquals(attrs, data.attributes)

        val response = PlanningCenterClient.PersonResponse(data = data)
        assertEquals(data, response.data)
    }

    @Test
    fun `dtos with an omitted field fall back to their default`() {
        assertEquals(PlanningCenterClient.PersonData(), PlanningCenterClient.PersonResponse().data)
        assertEquals("", PlanningCenterClient.PersonData().id)
        assertEquals(null, PlanningCenterClient.PersonAttributes().name)
        assertEquals("", PlanningCenterClient.TokenResponse().accessToken)
        assertEquals("", PlanningCenterClient.TokenResponse().refreshToken)
        assertEquals(0L, PlanningCenterClient.TokenResponse().expiresIn)
    }
}
