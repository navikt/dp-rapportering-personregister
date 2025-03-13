package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeldepliktendringMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        MeldepliktendringMottak(testRapid, personMediator, fremtidigHendelseMediator, meldepliktendringMetrikker)
    }

    @Test
    fun `kan motta meldepliktendring event`() {
        testRapid.sendTestMessage(meldepliktendring_event())
        verify(exactly = 1) { personMediator.behandle(any<MeldepliktHendelse>()) }
    }

    @Test
    fun `kan motta meldepliktendring event med 'datoTil'`() {
        testRapid.sendTestMessage(meldepliktendring_event(datoTil = "2025-03-01 00:00:00"))

        verify(exactly = 1) { personMediator.behandle(any<MeldepliktHendelse>()) }
    }

    @Test
    fun `kan motta fremtidig meldepliktendring event`() {
        testRapid.sendTestMessage(
            meldepliktendring_event(
                datoFra =
                    LocalDateTime.now().plusDays(1).format(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                    ),
            ),
        )

        verify(exactly = 1) { fremtidigHendelseMediator.behandle(any()) }
    }
}

private fun meldepliktendring_event(
    datoFra: String = "2025-02-01 00:00:00",
    datoTil: String? = null,
) = //language=json
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
        "DATO_FRA": "$datoFra",
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
