package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.DpSoknad
import no.nav.dagpenger.rapportering.personregister.modell.Status.Søkt
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class InMemoryHendelseRepositoryTest {
    private lateinit var hendelseRepository: HendelseRepository
    private lateinit var hendelse: Hendelse

    @BeforeEach
    fun setUp() {
        hendelseRepository = InMemoryHendelseRepository()
        hendelse =
            Hendelse(
                id = UUID.randomUUID(),
                ident = "12345678910",
                referanseId = "123",
                mottatt = LocalDateTime.now(),
                beskrivelse = Søkt.name,
                kilde = DpSoknad,
            )
    }

    @Test
    fun `kan opprette en hendelse`() {
        hendelseRepository.opprettHendelse(hendelse)
        hendelseRepository.finnHendelser(hendelse.ident) shouldBe listOf(hendelse)
    }

    @Test
    fun `kan ikke funne en gendelse som ikke er lagret`() {
        hendelseRepository.finnHendelser(hendelse.ident) shouldBe emptyList()
    }
}
