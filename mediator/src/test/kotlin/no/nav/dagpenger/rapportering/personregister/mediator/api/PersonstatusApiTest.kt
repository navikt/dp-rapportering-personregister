package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Søknad
import no.nav.dagpenger.rapportering.personregister.modell.Status.Søkt
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

@Disabled
class PersonstatusApiTest : ApiTestSetup() {
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
            opprettPerson(ident)
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

fun opprettPerson(
    ident: String,
    hendelse: Hendelse = lagHendelse(ident),
) {
    using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "INSERT INTO person (ident) VALUES (:ident)",
                mapOf("ident" to ident),
            ).asUpdate,
        )
    }.also { println("Opprettet person med ident $ident") }

    using(sessionOf(dataSource)) { session ->
        session.run(
            queryOf(
                "INSERT INTO hendelse (id, ident, referanse_id, dato, status, kilde) VALUES (?, ?, ?, ?, ?, ?)",
                hendelse.id,
                hendelse.ident,
                hendelse.referanseId,
                hendelse.dato,
                hendelse.status.name,
                hendelse.kilde.name,
            ).asUpdate,
        )
    }.also { println("Opprettet hendelse for person med ident $ident") }
}

fun lagHendelse(ident: String) =
    Hendelse(id = UUID.randomUUID(), ident = ident, referanseId = "123", dato = LocalDateTime.now(), status = Søkt, kilde = Søknad)
