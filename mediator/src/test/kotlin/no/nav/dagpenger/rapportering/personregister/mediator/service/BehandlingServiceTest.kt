package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakFattetUtenforArenaHendelse
import kotlin.test.Test

class BehandlingServiceTest {
    @Test
    fun `behandler VedtakFattetUtenforArenaHendelse`() {
        val ident = "01020312345"
        val id = UUIDv7.newUuid()
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val sakId = UUIDv7.newUuid().toString()

        val person = mockk<Person>(relaxed = true)
        val personService = mockk<PersonService>()
        every { personService.hentEllerOpprettPerson(ident) } returns person
        every { personService.oppdaterPerson(person) } just runs

        val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)

        val behandlingService =
            BehandlingService(
                personService = personService,
                behandlingRepository = behandlingRepository,
                actionTimer = actionTimer,
            )

        behandlingService.behandle(
            VedtakFattetUtenforArenaHendelse(
                korrelasjonsId = id,
                ident = ident,
                referanseId = id.toString(),
                behandlingId = behandlingId,
                søknadId = søknadId,
                sakId = sakId,
            ),
        )

        verify(exactly = 1) { behandlingRepository.lagreData(behandlingId, søknadId, ident, sakId) }
        verify(exactly = 1) { personService.oppdaterPerson(person) }
    }
}
