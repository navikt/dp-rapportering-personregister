package no.nav.dagpenger.rapportering.personregister.mediator

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class FremtidigHendelseMediatorTest {
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val fremtidigHendelseMediator = FremtidigHendelseMediator(personRepository, actionTimer)
    private val ident = "12345678910"

    @Test
    fun `kan motta fremtidige hendelser`() {
        every { personRepository.finnesPerson(ident) } returns true

        val fremtidigDato = LocalDateTime.now().plusDays(1)
        fremtidigHendelseMediator.behandle(dagpengerMeldegruppeHendelse(fremtidigDato))
        fremtidigHendelseMediator.behandle(annenMeldegruppeHendelse(fremtidigDato))
        fremtidigHendelseMediator.behandle(meldepliktHendelse(fremtidigDato))

        verify(exactly = 3) { personRepository.lagreFremtidigHendelse(any()) }
    }

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato = dato, sluttDato = null, "DAGP", harMeldtSeg = false)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, startDato = dato, sluttDato = null, "ARBS", harMeldtSeg = false)

    private fun meldepliktHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = MeldepliktHendelse(
        ident = ident,
        dato = dato,
        startDato = dato,
        sluttDato = null,
        statusMeldeplikt = true,
        referanseId = referanseId,
        harMeldtSeg = false,
    )
}
