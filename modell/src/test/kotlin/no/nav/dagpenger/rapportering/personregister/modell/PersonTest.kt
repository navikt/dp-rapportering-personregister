package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonTest {
    private fun opprettHendelse(
        ident: String,
        referanseId: String,
        dato: LocalDateTime = LocalDateTime.now(),
        status: Status = Status.Søkt,
    ) = Hendelse(
        ident = ident,
        referanseId = referanseId,
        dato = dato,
        status = status,
        kilde = Kildesystem.Søknad,
    )

    @Test
    fun `kan behandle første hendelse`() {
        val ident = "12345678901"
        val dato = LocalDateTime.now()
        val referanseId = "123"

        val hendelse = opprettHendelse(ident, referanseId, dato)

        val person = Person(ident).apply { behandle(hendelse) }

        person.ident shouldBe ident
        person.status shouldBe Status.Søkt
    }

    @Test
    fun `kan behandle flere hendelser`() {
        val ident = "12345678901"
        val dato = LocalDateTime.now()
        val referanseId = "123"

        val person = Person(ident).apply { behandle(opprettHendelse(ident, referanseId, dato)) }
        val nyHendelse = opprettHendelse(ident, referanseId = "456", dato = LocalDateTime.now(), status = Status.Innvilget)
        person.behandle(nyHendelse)

        person.status shouldBe Status.Innvilget
        person.status(dato) shouldBe Status.Søkt
    }
}
