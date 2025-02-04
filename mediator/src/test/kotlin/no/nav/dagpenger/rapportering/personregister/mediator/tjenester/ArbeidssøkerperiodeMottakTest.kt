package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ArbeidssøkerperiodeMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        ArbeidssøkerperiodeMottak(testRapid, personstatusMediator)
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta arbeidssøkerperiode event`() {
        testRapid.sendTestMessage(arbeidssøkerperiode_event)

        verify(exactly = 1) { personstatusMediator.behandle(any<ArbeidssøkerHendelse>()) }
    }
}

val arbeidssøkerperiode_event =
    """
    {
      "id": "123e4567-e89b-12d3-a456-426614174000",
      "identitetsnummer": "12345678910",
      "startet": {
        "tidspunkt": 1672531200000,
        "utfoertAv": {
          "id": "system-user",
          "type": "SYSTEM"
        },
        "kilde": "system-api",
        "aarsak": "INITIAL_REGISTRATION",
        "tidspunktFraKilde": {
          "tidspunkt": 1672531200000,
          "avviksType": "EPOCH_MILLIS"
        }
      },
      "avsluttet": null
    }
    """.trimIndent()
