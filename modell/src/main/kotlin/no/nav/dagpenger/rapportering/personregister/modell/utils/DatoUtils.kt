package no.nav.dagpenger.rapportering.personregister.modell.utils

import java.time.LocalDate
import java.time.LocalDateTime

fun LocalDateTime?.erFortid() = this?.toLocalDate()?.isBefore(LocalDate.now()) ?: false

fun LocalDate.erFortidEllerIdag() = isBefore(LocalDate.now().plusDays(1))

fun LocalDate.erIdagEllerIFremtid() = isAfter(LocalDate.now().minusDays(1))
