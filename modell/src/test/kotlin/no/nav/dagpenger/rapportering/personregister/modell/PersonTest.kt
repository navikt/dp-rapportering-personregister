package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonTest {
    private val ident = "12345678901"
    private val referanseId = "123"
    private val nå = LocalDateTime.now()
    private val tidligere = nå.minusDays(1)

    @Test
    fun `Når søknaden innvilges, endres status fra SØKT til INNVILGET`() {
        val person =
            Person(
                ident,
                withStatusHistorikk(tidligere to SØKT),
            ).apply {
                behandle(innvilgelseHendelse(nå))
            }

        with(person) {
            status shouldBe INNVILGET
            status(tidligere) shouldBe SØKT
        }
    }

    @Test
    fun `Når søknaden avslås, endres status fra SØKT til AVSLÅTT`() {
        val person =
            Person(
                ident,
                withStatusHistorikk(tidligere to SØKT),
            )

        person.behandle(avslagHendelse())

        person.status shouldBe AVSLÅTT
        person.status(tidligere) shouldBe SØKT
    }

    @Test
    fun `Når dagpengene stanses, endres status fra INNVILGET til STANSET`() {
        val person = Person(ident, withStatusHistorikk(tidligere to INNVILGET))

        person.behandle(stansHendelse())

        person.status shouldBe STANSET
        person.status(tidligere) shouldBe INNVILGET
    }

    @Test
    fun `Når person får medhold i klagen, endres status fra AVSLÅTT til INNVILGET`() {
        val person = Person(ident, withStatusHistorikk(tidligere to AVSLÅTT))

        person.behandle(innvilgelseHendelse())

        person.status shouldBe INNVILGET
        person.status(tidligere) shouldBe AVSLÅTT
    }

    @Test
    fun `Når dagpengene gjenopptas, endres status fra STANSET til INNVILGET`() {
        val person = Person(ident, withStatusHistorikk(tidligere to STANSET))

        person.behandle(innvilgelseHendelse())

        person.status shouldBe INNVILGET
        person.status(tidligere) shouldBe STANSET
    }

    @Test
    fun `Når status overgangen er ugyldig, forblir status SØKT`() {
        val person = Person(ident, withStatusHistorikk(tidligere to SØKT))

        person.behandle(stansHendelse())

        person.status shouldBe SØKT
        person.status(tidligere) shouldBe SØKT
    }

    @Test
    fun `Når status overgangen er ugyldig, forblir status STANSET`() {
        val person = Person(ident, withStatusHistorikk(tidligere to STANSET))

        person.behandle(avslagHendelse())

        person.status shouldBe STANSET
        person.status(tidligere) shouldBe STANSET
    }

    @Test
    fun `Når man søker dagpenger på nytt etter avslag, endres status fra AVSLÅTT til SØKT`() {
        val person = Person(ident, withStatusHistorikk(tidligere to AVSLÅTT))

        person.behandle(søknadHendelse())

        person.status shouldBe SØKT
        person.status(tidligere) shouldBe AVSLÅTT
    }

    @Test
    fun `frasier ansvaret for innsending av bekreftelse på vegne av personen ved stans`() {
        val observer = mockk<PersonObserver>(relaxed = true)
        val person = Person(ident, withStatusHistorikk(tidligere to INNVILGET))

        person.addObserver(observer)
        person.behandle(stansHendelse())

        person.status shouldBe STANSET
        person.status(tidligere) shouldBe INNVILGET

        verify(exactly = 1) { observer.frasiArbeidssøkerBekreftelse(person) }
        // TODO Person har ingen aktive perioder
    }

    private fun søknadHendelse(dato: LocalDateTime = LocalDateTime.now()) =
        SøknadHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
        )

    private fun innvilgelseHendelse(dato: LocalDateTime = LocalDateTime.now()) =
        InnvilgelseHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
        )

    private fun avslagHendelse() =
        AvslagHendelse(
            ident = ident,
            dato = nå,
            referanseId = referanseId,
        )

    private fun stansHendelse(meldegruppeKode: String = "ARBS") =
        StansHendelse(
            ident = ident,
            dato = nå,
            referanseId = referanseId,
            meldegruppeKode = meldegruppeKode,
        )

    private fun withStatusHistorikk(vararg entries: Pair<LocalDateTime, Status>): TemporalCollection<Status> =
        TemporalCollection<Status>().apply {
            entries.forEach { (time, status) -> put(time, status) }
        }
}
