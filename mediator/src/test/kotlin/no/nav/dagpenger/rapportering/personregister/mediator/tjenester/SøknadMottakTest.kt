package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.soknadMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SøknadMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)

    init {
        SøknadMottak(testRapid, personMediator, soknadMetrikker)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta Quiz-søknad`() {
        val ident = "12345678901"
        val søknadId = UUID.randomUUID().toString()
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
    }

    @Test
    fun `skal motta Legacy-søknad`() {
        val ident = "12345678902"
        val søknadId = UUID.randomUUID().toString()
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
    }

    @Test
    fun `skal motta Papirsøknad`() {
        val ident = "12345678903"
        val søknadsData = null
        val dato = "2025-09-25T00:00:00"

        testRapid.sendTestMessage(lagInnsendingFerdigstiltEvent(ident, dato, søknadsData))

        verify(exactly = 1) { personMediator.behandle(any<SøknadHendelse>(), eq(1)) }
    }
}

private fun lagInnsendingFerdigstiltEvent(
    ident: String,
    dato: String,
    søknadsData: String?,
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
