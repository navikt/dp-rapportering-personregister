package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import org.junit.jupiter.api.Test

class MeldepliktendringMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        MeldepliktendringMottak(testRapid, personstatusMediator)
    }

    @Test
    fun `kan motta meldepliktendring event`() {
        testRapid.sendTestMessage(meldepliktendring_event())
        testRapid.inspektør
        verify(exactly = 1) { personstatusMediator.behandle(any<MeldepliktHendelse>()) }
    }

    @Test
    fun `kan motta meldepliktendring event med 'datoTil'`() {
        println(meldepliktendring_event("2025-03-01 00:00:00"))
        testRapid.sendTestMessage(meldepliktendring_event("2025-03-01 00:00:00"))

        testRapid.inspektør

        verify(exactly = 1) { personstatusMediator.behandle(any<MeldepliktHendelse>()) }
    }
}

private fun meldepliktendring_event(datoTil: String? = null) =
    //language=json
    """
    {
    "table": "ARENA_GOLDENGATE.MELDEPLIKT",
    "op_type": "I",
    "op_ts": "2025-02-08 14:00:26.000000",
    "current_ts": "2025-02-08T14:00:30.541000",
    "pos": "00000000120001447854",
    "after": {
        "MELDEPLIKT_ID": 30321659,
        "STATUS_MELDEPLIKT": "J",
        "DATO_FRA": "2025-02-01 00:00:00",
        "DATO_TIL": ${if (datoTil == null) null else "\"$datoTil\""},
        "HENDELSESDATO": "2025-02-08 14:00:25",
        "STATUS_AKTIV": "J",
        "BEGRUNNELSE": "QWt0aUYIUuuiiuU6ffVuIHl0ZWxzZXI=",
        "PERSON_ID": 4812036,
        "FODSELSNR": "12345678910",
        "HENDELSE_ID": 3205922,
        "OPPRETTET_DATO": "2025-02-08 14:00:25",
        "OPPRETTET_AV": "HBB4405",
        "ENDRET_DATO": "2025-02-08 14:00:25",
        "ENDRET_AV": "HBB4405"
    }
}
    """.trimIndent()
