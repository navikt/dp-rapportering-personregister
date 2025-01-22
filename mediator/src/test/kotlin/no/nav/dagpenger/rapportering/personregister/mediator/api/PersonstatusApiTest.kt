package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonstatusApiTest : ApiTestSetup() {
    private val id = UUID.randomUUID()
    private val ident = "12345678910"

    @Test
    fun `personstatus uten token gir unauthorized`() =
        setUpTestApplication {
            with(client.get("/personstatus")) {
                status shouldBe HttpStatusCode.Unauthorized
            }
        }

    @Test
    fun `personstatus gir not found hvis personen ikke finnes`() =
        setUpTestApplication {
            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.NotFound
            }
        }

    @Test
    fun `personstatus gir personen hvis den finnes`() =
        setUpTestApplication {
            val personRepository = PostgresPersonRepository(PostgresDataSourceBuilder.dataSource, actionTimer)

            Person(ident)
                .apply { behandle(lagHendelse(ident)) }
                .also {
                    personRepository.lagrePerson(it)
                }

            with(
                client.get("/personstatus") {
                    header(HttpHeaders.Authorization, "Bearer ${issueToken(ident)}")
                },
            ) {
                status shouldBe HttpStatusCode.OK
                defaultObjectMapper.readTree(bodyAsText())["ident"].asText() shouldBe ident
            }
        }
}

fun lagHendelse(ident: String) =
    SøknadHendelse(
        ident = ident,
        referanseId = "123",
        dato = LocalDateTime.now(),
    )
