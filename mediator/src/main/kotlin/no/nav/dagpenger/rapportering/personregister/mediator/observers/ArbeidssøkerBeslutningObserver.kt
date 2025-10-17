package no.nav.dagpenger.rapportering.personregister.mediator.observers

import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerBeslutningRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Handling
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import java.util.UUID

class ArbeidssøkerBeslutningObserver(
    private val arbeidssøkerBeslutningRepository: ArbeidssøkerBeslutningRepository,
) : PersonObserver {
    override fun overtattArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {
        person.arbeidssøkerperioder.gjeldende
            ?.let { periode ->
                val beslutning =
                    ArbeidssøkerBeslutning(
                        person.ident,
                        periode.periodeId,
                        Handling.OVERTATT,
                        begrunnelse =
                            "Oppfyller krav: arbedissøker, " +
                                "meldeplikt=${person.meldeplikt}," +
                                " gruppe=${person.meldegruppe}," +
                                " og harRettTilDp=${person.harRettTilDp}",
                    )

                arbeidssøkerBeslutningRepository.lagreBeslutning(beslutning)
            }
    }

    override fun frasagtArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
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
                                " meldeplikt=${person.meldeplikt}," +
                                " gruppe=${person.meldegruppe}" +
                                " og harRettTilDp=${person.harRettTilDp}",
                    )

                arbeidssøkerBeslutningRepository.lagreBeslutning(beslutning)
            }
    }
}
