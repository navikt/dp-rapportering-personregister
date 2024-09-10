package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.fabrikk.modell.Dag
import no.nav.dagpenger.rapportering.fabrikk.modell.Periode
import no.nav.dagpenger.rapportering.fabrikk.modell.Rapporteringsperiode
import java.time.LocalDate

class Mediator {
    fun behandle(
        ident: String,
        periodeStart: LocalDate,
    ) {
        Rapporteringsperiode(
            periode = Periode(fraOgMed = periodeStart),
            dager =
                (0..13).map { dagIndex ->
                    Dag(
                        dato = periodeStart.plusDays(dagIndex.toLong()),
                        dagIndex = dagIndex,
                    )
                },
        )
    }
}
