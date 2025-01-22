package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonTest {
    private val ident = "12345678901"
    private val referanseId = "123"
    private val dato = LocalDateTime.now()

    @Test
    fun `Når søknaden innvilges, endres status fra SØKT til INNVILGET`() {
        val person = Person(ident, SØKT)

        person.behandle(innvilgelseHendelse())

        person.status shouldBe INNVILGET
    }

    @Test
    fun `Når søknaden avslås, endres status fra SØKT til AVSLÅTT`() {
        val person = Person(ident, SØKT)

        person.behandle(avslagHendelse())

        person.status shouldBe AVSLÅTT
    }

    @Test
    fun `Når dagpengene stanses, endres status fra INNVILGET til STANSET`() {
        val person = Person(ident, INNVILGET)

        person.behandle(stansHendelse())

        person.status shouldBe STANSET
    }

    @Test
    fun `Når person får medhold i klagen, endres status fra AVSLÅTT til INNVILGET`() {
        val person = Person(ident, AVSLÅTT)

        person.behandle(innvilgelseHendelse())

        person.status shouldBe INNVILGET
    }

    @Test
    fun `Når dagpengene gjenopptas, endres status fra STANSET til INNVILGET`() {
        val person = Person(ident, STANSET)

        person.behandle(innvilgelseHendelse())

        person.status shouldBe INNVILGET
    }

    @Test
    fun `Når status overgangen er ugyldig, forblir status SØKT`() {
        val person = Person(ident, SØKT)

        person.behandle(stansHendelse())

        person.status shouldBe SØKT
    }

    @Test
    fun `Når status overgangen er ugyldig, forblir status STANSET`() {
        val person = Person(ident, STANSET)

        person.behandle(avslagHendelse())

        person.status shouldBe STANSET
    }

    @Test
    fun `Når man søker dagpenger på nytt etter avslag, endres status fra AVSLÅTT til SØKT`() {
        val person = Person(ident, AVSLÅTT)

        person.behandle(søknadHendelse())

        person.status shouldBe SØKT
    }

    private fun innvilgelseHendelse() =
        InnvilgelseHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
        )

    private fun avslagHendelse() =
        AvslagHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
        )

    private fun søknadHendelse(referanseId: String = this.referanseId) =
        SøknadHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
        )

    private fun stansHendelse(meldegruppeKode: String = "ARBS") =
        StansHendelse(
            ident = ident,
            dato = dato,
            referanseId = referanseId,
            meldegruppeKode = meldegruppeKode,
        )
}
