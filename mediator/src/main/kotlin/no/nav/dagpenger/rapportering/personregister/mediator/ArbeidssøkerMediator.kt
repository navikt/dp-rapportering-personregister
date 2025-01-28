package no.nav.dagpenger.rapportering.personregister.mediator

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
import no.nav.dagpenger.rapportering.personregister.modell.ArbeidssøkerperiodeLøsning
import no.nav.dagpenger.rapportering.personregister.modell.OvertaBekreftelseLøsning

class ArbeidssøkerMediator(
    private val arbeidssøkerService: ArbeidssøkerService,
) {
    fun behandle(arbeidssøkerperiodeLøsning: ArbeidssøkerperiodeLøsning) {
        if (arbeidssøkerperiodeLøsning.feil != null) {
            sikkerlogg.error {
                "Feil ved henting av arbeidssøkerperiode for person med ident ${arbeidssøkerperiodeLøsning.ident}. " +
                    "Prøver å hente status igjen."
            }
            arbeidssøkerService.sendArbeidssøkerBehov(arbeidssøkerperiodeLøsning.ident)
        } else if (arbeidssøkerperiodeLøsning.løsning != null) {
            val arbeidssøkerperiode = arbeidssøkerperiodeLøsning.løsning!!

            // Gjør ikke noe hvis person ikke finnes i databasen fra før
            if (!arbeidssøkerService.finnesPerson(arbeidssøkerperiode.ident)) {
                sikkerlogg.info { "Personen arbeidssøkerperioden gjelder for er ikke dagpengebruker." }
                return
            }

            val lagredePerioder = arbeidssøkerService.hentArbeidssøkerperioder(arbeidssøkerperiode.ident)
            // Hvis perioden ikke finnes fra før, lagres den og overtgelsesbehov sendes
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
    }

    fun behandle(overtaBekreftelseLøsning: OvertaBekreftelseLøsning) {
        try {
            if (overtaBekreftelseLøsning.feil == null) {
                arbeidssøkerService.oppdaterOvertagelse(overtaBekreftelseLøsning.periodeId, true)
                sikkerlogg.info { "Bekreftelse for periode ${overtaBekreftelseLøsning.periodeId} overtatt." }
            } else {
                sikkerlogg.error {
                    "Feil ved overtagelse av bekreftelse for periode ${overtaBekreftelseLøsning.periodeId}." +
                        "\n${overtaBekreftelseLøsning.feil}. Sender overtakelse på nytt."
                }
                arbeidssøkerService.sendOvertaBekreftelseBehov(overtaBekreftelseLøsning.ident, overtaBekreftelseLøsning.periodeId)
            }
        } catch (e: Exception) {
            sikkerlogg.error(e) {
                "Feil ved behandling av løsning for overtagelse av bekreftelse. " +
                    "Prøver ikke å overta bekreftelse på nytt. $overtaBekreftelseLøsning"
            }
            throw e
        }
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }
}
