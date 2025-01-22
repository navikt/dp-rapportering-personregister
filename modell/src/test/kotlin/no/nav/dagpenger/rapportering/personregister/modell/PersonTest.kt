package no.nav.dagpenger.rapportering.personregister.modell

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonTest {
    @Test
    fun `kan behandle første hendelse`() {
        val ident = "12345678901"
        val dato = LocalDateTime.now()
        val referanseId = "123"

        val hendelse = SøknadHendelse(ident, dato, referanseId)

        val person = Person(ident).apply { behandle(hendelse) }

        person.ident shouldBe ident
        println("person: ${person.status.type}")
        person.status.type shouldBe Status.Type.SØKT
    }

    @Test
    fun `kan behandle flere hendelser`() {
        val ident = "12345678901"
        val dato = LocalDateTime.now()
        val referanseId = "123"

        val søknadHendelse = SøknadHendelse(ident, dato, referanseId)
        val person = Person(ident).apply { behandle(søknadHendelse) }

        val nyHendelse = InnvilgelseHendelse(ident, dato = LocalDateTime.now(), referanseId = "456")
        person.behandle(nyHendelse)

        person.status.type shouldBe Status.Type.INNVILGET
    }
}
