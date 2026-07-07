package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

data class Søknad(
    val søknadId: String,
    val innsendtTidspunkt: LocalDateTime,
)
