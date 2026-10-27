package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDate

data class ArbeidssøkerperiodeUtmeldingInfo(
    val årsakTilUtmelding: Arbeidssøkerperiode.ÅrsakTilUtmelding,
    val datoForUtmelding: LocalDate,
)
