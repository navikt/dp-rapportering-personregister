package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDate

data class RegistrertSomArbeidssøkerLøsning(
    val ident: String,
    val verdi: Boolean,
    val gyldigFraOgMed: LocalDate,
    val gyldigTilOgMed: LocalDate,
)
