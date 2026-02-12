package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.søknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class SøknadMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)

    init {
        SøknadMottak(testRapid, personMediator, søknadMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler Quiz-søknad og inkrementerer metrikk`() {
        val metrikkCount = søknadMetrikker.søknaderMottatt.count()
        val ident = "12345678901"
        val søknadId = UUIDv7.newUuid().toString()
        val søknadsData = "\"søknad_uuid\": \"$søknadId\""
        val dato = "2025-09-23T00:00:00"

        testRapid.sendTestMessage(lagInnsendingFerdigstiltEvent(ident, dato, søknadsData))

        val søknadHendelse =
            SøknadHendelse(
                ident,
                dato.toLocalDateTime(),
                dato.toLocalDateTime(),
                søknadId,
            )

        verify(exactly = 1) { personMediator.behandle(søknadHendelse) }
        søknadMetrikker.søknaderMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket behandler Legacy-søknad og inkrementerer metrikk`() {
        val metrikkCount = søknadMetrikker.søknaderMottatt.count()
        val ident = "12345678902"
        val søknadId = UUIDv7.newUuid().toString()
        val søknadsData = "\"brukerBehandlingId\": \"$søknadId\""
        val dato = "2025-09-24T00:00:00"

        testRapid.sendTestMessage(lagInnsendingFerdigstiltEvent(ident, dato, søknadsData))

        val søknadHendelse =
            SøknadHendelse(
                ident,
                dato.toLocalDateTime(),
                dato.toLocalDateTime(),
                søknadId,
            )

        verify(exactly = 1) { personMediator.behandle(søknadHendelse) }
        søknadMetrikker.søknaderMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket behandler Papirsøknad og inkrementerer metrikk`() {
        val metrikkCount = søknadMetrikker.søknaderMottatt.count()
        val ident = "12345678903"
        val søknadsData = null
        val dato = "2025-09-25T00:00:00"

        testRapid.sendTestMessage(lagInnsendingFerdigstiltEvent(ident, dato, søknadsData))

        verify(exactly = 1) { personMediator.behandle(any<SøknadHendelse>(), eq(1)) }
        søknadMetrikker.søknaderMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `onPacket kaster exception og inkrementerer feilmetrikk hvis behandling av melding feiler`() {
        val metrikkCount = søknadMetrikker.søknaderFeilet.count()
        every { personMediator.behandle(any<SøknadHendelse>()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(lagInnsendingFerdigstiltEvent())
            }

        exception.message shouldBe "kaboom"
        søknadMetrikker.søknaderFeilet.count() shouldBe metrikkCount + 1
    }
}

private fun lagInnsendingFerdigstiltEvent(
    ident: String = "12345678903",
    dato: String = "2025-09-25T00:00:00",
    søknadsData: String? = null,
): String {
    //language=json
    return """
        {
        	"@id": "7b4d1240-35ef-413f-b7e0-e0ef6e7677e9",
        	"@opprettet": "2025-09-25T17:26:30.406457503",
        	"journalpostId": "721541000",
        	"datoRegistrert": "$dato",
        	"skjemaKode": "NAV 04-01.04",
        	"tittel": "NAV 04-01.04 Søknad om dagpenger ved permittering",
        	"type": "NySøknad",
        	"fødselsnummer": "$ident",
        	"aktørId": "1000012345678",
        	"fagsakId": "null",
        	"søknadsData": {
              ${søknadsData ?: ""}
            },
        	"@event_name": "innsending_ferdigstilt",
        	"bruk-dp-behandling": true,
        	"system_read_count": 0,
        	"system_participating_services": [
        		{
        			"id": "7b4d1240-35ef-413f-b7e0-e0ef6e7677e9",
        			"time": "2025-09-25T17:26:30.406621723",
        			"service": "dp-mottak",
        			"instance": "dp-mottak-77c76f7895-tqlq6",
        			"image": "europe-north1-docker.pkg.dev/nais-management-233d/teamdagpenger/dp-mottak:2025.09.24-06.59-661ca55"
        		}
        	]
        }
        """.trimIndent()
}

private fun String.toLocalDateTime() = LocalDateTime.parse(this)
