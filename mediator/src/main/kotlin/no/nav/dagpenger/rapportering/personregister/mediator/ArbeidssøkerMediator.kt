package no.nav.dagpenger.rapportering.personregister.mediator

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode

class ArbeidssøkerMediator(
    private val arbeidssøkerService: ArbeidssøkerService,
) {
    fun behandle(ident: String) {
        try {
            val arbeidssøkerperiode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }
            if (arbeidssøkerperiode != null) {
                behandle(arbeidssøkerperiode)
            } else {
                sikkerlogg.info { "Personen er ikke arbeidssøker" }
            }
        } catch (e: Exception) {
            sikkerlogg.error(e) { "Feil ved behandling av arbeidssøkerperiode for ident $ident" }
        }
    }

    fun behandle(arbeidssøkerperiode: Arbeidssøkerperiode) {
        // Gjør ikke noe hvis person ikke finnes i databasen fra før
        if (!arbeidssøkerService.finnesPerson(arbeidssøkerperiode.ident)) {
            sikkerlogg.info { "Personen arbeidssøkerperioden gjelder for er ikke dagpengebruker." }
            return
        }

        val lagredePerioder = arbeidssøkerService.hentLagredeArbeidssøkerperioder(arbeidssøkerperiode.ident)
        // Hvis perioden ikke finnes fra før, lagres den og overtakelsesbehov sendes
        if (lagredePerioder.none { it.periodeId == arbeidssøkerperiode.periodeId }) {
            try {
                arbeidssøkerService.lagreArbeidssøkerperiode(arbeidssøkerperiode)
                sikkerlogg.info { "Ny periode lagret. $arbeidssøkerperiode " }
                if (arbeidssøkerperiode.avsluttet == null) {
                    arbeidssøkerService.sendOvertaBekreftelseBehov(
                        arbeidssøkerperiode.ident,
                        arbeidssøkerperiode.periodeId,
                    )
                    sikkerlogg.info { "Sendte overtagelsesbehov for perioden: $arbeidssøkerperiode " }
                }
            } catch (e: IllegalStateException) {
                sikkerlogg.error(e) { "Behandlet ikke arbeidssøkerperiode $arbeidssøkerperiode" }
            }
        } else {
            // Hvis perioden finnes fra før, sjekk om overtagelsebehov må sendes og om avsluttet dato har endret seg
            lagredePerioder
                .find { it.periodeId == arbeidssøkerperiode.periodeId }
                ?.let { lagretPeriode ->
                    if (arbeidssøkerperiode.avsluttet == null && lagretPeriode.overtattBekreftelse != true) {
                        sikkerlogg.info { "Sender overtagelsesbehov for periode ${arbeidssøkerperiode.periodeId}" }
                        arbeidssøkerService.sendOvertaBekreftelseBehov(
                            arbeidssøkerperiode.ident,
                            arbeidssøkerperiode.periodeId,
                        )
                    }
                    if (arbeidssøkerperiode.avsluttet != null && lagretPeriode.avsluttet != arbeidssøkerperiode.avsluttet) {
                        sikkerlogg.info { "Oppdaterer arbeidssøkerperiode ${arbeidssøkerperiode.periodeId} med avsluttet dato" }
                        arbeidssøkerService.avsluttPeriodeOgOppdaterOvertagelse(arbeidssøkerperiode)
                    }
                }
        }
        // TODO("Edgecase: Hvordan sjekker vi om periodeId har endret seg?")
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
