package no.nav.dagpenger.rapportering.personregister.mediator.observers

import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende

class ArbeidssøkerBeslutningObserver(
    private val beslutningRepository: ArbeidssøkerBeslutningRepository,
) : PersonObserver {
    override fun overtaArbeidssøkerBekreftelse(person: Person) {
        person.arbeidssøkerperioder.gjeldende
            ?.let { periode ->
                val beslutning =
                    ArbeidssøkerBeslutning(
                        person.ident,
                        periode.periodeId,
                        Handling.OVERTATT,
                        begrunnelse =
                            "Oppfyller krav: arbedissøker, " +
                                "meldeplikt=${person.meldeplikt} " +
                                "og gruppe=${person.meldegruppe}",
                    )

                beslutningRepository.lagreBeslutning(beslutning)
            }
    }

    override fun frasiArbeidssøkerBekreftelse(
        person: Person,
        fristBrutt: Boolean,
    ) {
        person.arbeidssøkerperioder.gjeldende
            ?.let { periode ->
                val beslutning =
                    ArbeidssøkerBeslutning(
                        person.ident,
                        periode.periodeId,
                        Handling.FRASAGT,
                        begrunnelse =
                            "Oppfyller ikke krav: " +
                                "arbedissøker: ${person.erArbeidssøker}," +
                                "meldeplikt=${person.meldeplikt} " +
                                "og gruppe=${person.meldegruppe}",
                    )

                beslutningRepository.lagreBeslutning(beslutning)
            }
    }
}
