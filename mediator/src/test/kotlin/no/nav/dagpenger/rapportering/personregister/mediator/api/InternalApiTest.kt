package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class InternalApiTest : ApiTestSetup() {
    @Test
    fun `isAlive svarer OK`() =
        setUpTestApplication {
            with(client.get("/isAlive")) {
                status shouldBe HttpStatusCode.OK
            }
        }

    @Test
    fun `isReady svarer OK`() =
        setUpTestApplication {
            with(client.get("/isReady")) {
                status shouldBe HttpStatusCode.OK
            }
        }
}
