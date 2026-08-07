package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.OptimisticLockingException
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakFattetUtenforArenaHendelse

private val logger = KotlinLogging.logger {}

class BehandlingService(
    private val personService: PersonService,
    private val behandlingRepository: BehandlingRepository,
    private val actionTimer: ActionTimer,
) {
    fun behandle(
        hendelse: VedtakFattetUtenforArenaHendelse,
        counter: Int = 1,
    ) {
        actionTimer.timedAction("behandle_VedtakFattetUtenforArenaHendelse") {
            logger.info { "Behandler VedtakFattetUtenforArenaHendelse: ${hendelse.referanseId}" }

            behandlingRepository.lagreData(hendelse.behandlingId, hendelse.søknadId, hendelse.ident, hendelse.sakId)

            val person = personService.hentEllerOpprettPerson(hendelse.ident)
            person.hendelser.add(hendelse)

            try {
                personService.oppdaterPerson(person)
            } catch (e: OptimisticLockingException) {
                logger.info(e) {
                    "Optimistisk låsing feilet ved oppdatering av person med referanseId ${hendelse.referanseId}. Counter: $counter"
                }
                behandle(hendelse, counter + 1)
                return@timedAction
            }
        }
    }
}
