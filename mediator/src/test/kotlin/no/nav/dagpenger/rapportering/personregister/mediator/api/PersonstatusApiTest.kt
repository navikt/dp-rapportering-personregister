package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Test

class PersonstatusApiTest : ApiTestSetup() {
    private val ident = "12345678910"

    @Test
    fun `personstatus uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.get("/personstatus")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }
}
