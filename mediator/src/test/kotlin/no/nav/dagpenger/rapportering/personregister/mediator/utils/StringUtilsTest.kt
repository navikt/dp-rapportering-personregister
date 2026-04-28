package no.nav.dagpenger.rapportering.personregister.mediator.utils

import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class StringUtilsTest {
    @Test
    fun `validerIdent kaster ikke exception hvis ident inneholder 11 siffer`() {
        shouldNotThrowAny { "12345678901".validerIdent() }
    }

    @ParameterizedTest
    @ValueSource(strings = ["", "a", "1", "QWERTYUIOPÅ", "123456789012", "Q1W2E3R4T5Y'"])
    fun `validerIdent kaster exception hvis ident inneholder ugyldige kombinasjoner av tall, bokstaver og lengden på ident`(
        ident: String,
    ) {
        val exception = shouldThrow<IllegalArgumentException> { ident.validerIdent() }

        exception.message shouldBe "Person-ident må ha 11 sifre"
    }
}
