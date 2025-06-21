package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeldepliktendringMottakTest {
    private val testRapid = TestRapid()
    private val meldepliktMediator = mockk<MeldepliktMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    private val ident = "123456478901"
    private val hendelsesdato = LocalDateTime.now().format()
    private val hendelseId = "123"
    private val meldepliktId = 1234567890

    init {
        MeldepliktendringMottak(testRapid, meldepliktMediator, fremtidigHendelseMediator, meldepliktendringMetrikker)
    }

    @Test
    fun `kan motta meldepliktendring event`() {
        val datoFra = LocalDateTime.now().format()

        testRapid.sendTestMessage(
            meldepliktendring_event(
                ident = ident,
                hendelseId = hendelseId,
                hendelsesdato = hendelsesdato,
                meldepliktId = meldepliktId,
                datoFra = datoFra.format(),
            ),
        )

        val forventetHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                referanseId = "MP$meldepliktId",
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                statusMeldeplikt = true,
                harMeldtSeg = true,
            )

        verify(exactly = 1) { meldepliktMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan motta meldepliktendring event med 'datoTil'`() {
        val datoFra = LocalDateTime.now().format()
        val datoTil = LocalDateTime.now().plusDays(14).format()

        testRapid.sendTestMessage(
            meldepliktendring_event(
                ident = ident,
                hendelseId = hendelseId,
                hendelsesdato = hendelsesdato,
                meldepliktId = meldepliktId,
                datoFra = datoFra.format(),
                datoTil = datoTil.format(),
            ),
        )

        val forventetHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                referanseId = "MP$meldepliktId",
                startDato = datoFra.toLocalDateTime(),
                sluttDato = datoTil.toLocalDateTime(),
                statusMeldeplikt = true,
                harMeldtSeg = true,
            )

        verify(exactly = 1) { meldepliktMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan motta fremtidig meldepliktendring event`() {
        val datoFra = LocalDateTime.now().plusDays(1).format()

        testRapid.sendTestMessage(
            meldepliktendring_event(
                ident = ident,
                hendelseId = hendelseId,
                hendelsesdato = hendelsesdato,
                meldepliktId = meldepliktId,
                datoFra = datoFra.format(),
            ),
        )

        val forventetHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                referanseId = "MP$meldepliktId",
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                statusMeldeplikt = true,
                harMeldtSeg = true,
            )

        verify(exactly = 1) { fremtidigHendelseMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `skal ikke motta meldepliktendring som ikke er aktiv`() {
        val datoFra = LocalDateTime.now().plusDays(1).format()

        testRapid.sendTestMessage(
            meldepliktendring_event(
                ident = ident,
                hendelseId = hendelseId,
                hendelsesdato = hendelsesdato,
                meldepliktId = meldepliktId,
                datoFra = datoFra.format(),
                statusAktiv = "N",
            ),
        )

        val forventetHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                referanseId = "MP$meldepliktId",
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                statusMeldeplikt = false,
                harMeldtSeg = true,
            )

        verify(exactly = 0) { fremtidigHendelseMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan motta meldepliktendring med meldt seg lik nei`() {
        val datoFra = LocalDateTime.now().plusDays(1).format()

        testRapid.sendTestMessage(
            meldepliktendring_event(
                ident = ident,
                hendelseId = hendelseId,
                hendelsesdato = hendelsesdato,
                meldepliktId = meldepliktId,
                datoFra = datoFra.format(),
                statusAktiv = "J",
                harMeldtSeg = "N",
            ),
        )

        val forventetHendelse =
            MeldepliktHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                referanseId = "MP$meldepliktId",
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                statusMeldeplikt = true,
                harMeldtSeg = false,
            )

        verify(exactly = 1) { fremtidigHendelseMediator.behandle(forventetHendelse) }
    }
}

private fun meldepliktendring_event(
    ident: String,
    hendelseId: String,
    hendelsesdato: String,
    meldepliktId: Int,
    datoFra: String = "2025-02-01 00:00:00",
    datoTil: String? = null,
    statusAktiv: String = "J",
    harMeldtSeg: String = "J",
) = //language=json
    """
    {
    "table": "ARENA_GOLDENGATE.MELDEPLIKT",
    "op_type": "I",
    "op_ts": "2025-02-08 14:00:26.000000",
    "current_ts": "2025-02-08T14:00:30.541000",
    "pos": "00000000120001447854",
    "after": {
        "MELDEPLIKT_ID": $meldepliktId,
        "STATUS_MELDEPLIKT": "J",
        "DATO_FRA": "$datoFra",
        "DATO_TIL": ${if (datoTil == null) null else "\"$datoTil\""},
        "HENDELSESDATO": "$hendelsesdato",
        "STATUS_AKTIV": "$statusAktiv",
        "BEGRUNNELSE": "QWt0aUYIUuuiiuU6ffVuIHl0ZWxzZXI=",
        "PERSON_ID": 4812036,
        "FODSELSNR": "$ident",
        "HENDELSE_ID": $hendelseId,
        "OPPRETTET_DATO": "2025-02-08 14:00:25",
        "OPPRETTET_AV": "HBB4405",
        "ENDRET_DATO": "2025-02-08 14:00:25",
        "ENDRET_AV": "HBB4405",
        "HAR_MELDT_SEG": "$harMeldtSeg"
    }
}
    """.trimIndent()

private fun String.toLocalDateTime() = LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

private fun LocalDateTime.format(): String = this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
