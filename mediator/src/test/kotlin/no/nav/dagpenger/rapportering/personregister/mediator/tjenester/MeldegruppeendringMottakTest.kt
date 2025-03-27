package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.meldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MeldegruppeendringMottakTest {
    private val testRapid = TestRapid()
    private val personMediator = mockk<PersonMediator>()
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        MeldegruppeendringMottak(testRapid, personMediator, fremtidigHendelseMediator, meldegruppeendringMetrikker)
    }

    private val ident = "123456478901"
    private val hendelsesdato = LocalDateTime.now().format()
    private val referanseId = "123"
    private val meldegruppeId = 1234567890

    @Test
    fun `kan ta imot DAPG-meldegruppeendring`() {
        val datoFra = LocalDateTime.now().format()
        val datoTil = null
        val meldegruppeKode = "DAGP"

        testRapid.sendTestMessage(
            lagMeldegruppeEndringEvent(
                ident,
                hendelsesdato,
                datoFra,
                datoTil,
                meldegruppeKode,
                referanseId,
                meldegruppeId,
            ),
        )
        val forventetHendelse =
            DagpengerMeldegruppeHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                meldegruppeKode = meldegruppeKode,
                referanseId = referanseId,
                arenaId = meldegruppeId,
            )

        verify(exactly = 1) { personMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan ta imot annen meldegruppeendring enn DAGP`() {
        val datoFra = "2021-05-17 00:00:00"
        val datoTil = null
        val meldegruppeKode = "ARBS"

        testRapid.sendTestMessage(
            lagMeldegruppeEndringEvent(
                ident,
                hendelsesdato,
                datoFra,
                datoTil,
                meldegruppeKode,
                referanseId,
                meldegruppeId,
            ),
        )

        val forventetHendelse =
            AnnenMeldegruppeHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                startDato = datoFra.toLocalDateTime(),
                sluttDato = null,
                meldegruppeKode = meldegruppeKode,
                referanseId = referanseId,
                arenaId = meldegruppeId,
            )

        verify(exactly = 1) { personMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan ta imot meldegruppeendring enn med sluttdato`() {
        val datoFra = LocalDateTime.now().format()
        val datoTil = LocalDateTime.now().plusDays(1).format()
        val meldegruppeKode = "ARBS"

        testRapid.sendTestMessage(
            lagMeldegruppeEndringEvent(
                ident,
                hendelsesdato,
                datoFra,
                datoTil,
                meldegruppeKode,
                referanseId,
                meldegruppeId,
            ),
        )

        val forventetHendelse =
            AnnenMeldegruppeHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                startDato = datoFra.toLocalDateTime(),
                sluttDato = datoTil.toLocalDateTime(),
                meldegruppeKode = meldegruppeKode,
                referanseId = referanseId,
                arenaId = meldegruppeId,
            )

        verify(exactly = 1) { personMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `kan motta fremtidig meldegruppendring event`() {
        val datoFra = LocalDateTime.now().plusDays(1).format()
        val datoTil = "2021-06-08 14:05:10"
        val meldegruppeKode = "DAGP"

        testRapid.sendTestMessage(
            lagMeldegruppeEndringEvent(
                ident,
                hendelsesdato,
                datoFra,
                datoTil,
                meldegruppeKode,
                referanseId,
                meldegruppeId,
            ),
        )

        val forventetHendelse =
            DagpengerMeldegruppeHendelse(
                ident = ident,
                dato = hendelsesdato.toLocalDateTime(),
                startDato = datoFra.toLocalDateTime(),
                sluttDato = datoTil.toLocalDateTime(),
                meldegruppeKode = meldegruppeKode,
                referanseId = referanseId,
                arenaId = meldegruppeId,
            )

        verify(exactly = 1) { fremtidigHendelseMediator.behandle(forventetHendelse) }
    }

    @Test
    fun `skal ikke motta meldegruppendring som ikke er aktiv`() {
        val datoFra = LocalDateTime.now().plusDays(1).format()
        val datoTil = "2021-06-08 14:05:10"
        val meldegruppeKode = "DAGP"
        val statusAktiv = "N"

        testRapid.sendTestMessage(
            lagMeldegruppeEndringEvent(
                ident,
                hendelsesdato,
                datoFra,
                datoTil,
                meldegruppeKode,
                referanseId,
                meldegruppeId,
                statusAktiv,
            ),
        )

        verify(exactly = 0) { fremtidigHendelseMediator.behandle(any()) }
    }
}

private fun lagMeldegruppeEndringEvent(
    ident: String,
    hendelsesdato: String,
    datoFra: String,
    datoTil: String? = null,
    meldegruppeKode: String,
    referenseId: String,
    meldegruppeId: Int,
    statusAktiv: String = "J",
) = //language=json
    """
    {
    "table": "ARENA_GOLDENGATE.MELDEGRUPPE",
    "after": {
        "FODSELSNR": "$ident",
        "HENDELSE_ID": $referenseId,
        "HENDELSESDATO": "$hendelsesdato",
        "DATO_FRA": "$datoFra",
        "DATO_TIL": ${datoTil?.let { "\"$it\"" } ?: null},
        "MELDEGRUPPEKODE": "$meldegruppeKode",
        "MELDEGRUPPE_ID": $meldegruppeId,
        "STATUS_AKTIV": "$statusAktiv"
    }
    }
    
    """.trimIndent()

private fun String.toLocalDateTime() = LocalDateTime.parse(this, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))

private fun LocalDateTime.format(): String = this.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
