package no.nav.dagpenger.rapportering.personregister.modell.utils

import java.time.LocalDate
import java.time.LocalDateTime

fun LocalDateTime?.erDatoIFortid() = this?.toLocalDate()?.isBefore(LocalDate.now()) ?: false

fun LocalDateTime?.erIFortid() = this?.isBefore(LocalDateTime.now()) ?: false

fun LocalDateTime?.erIFremtid() = this?.isAfter(LocalDateTime.now()) ?: false

fun LocalDate.erIFortidEllerIdag() = isBefore(LocalDate.now().plusDays(1))

fun LocalDate.erIdagEllerIFremtid() = isAfter(LocalDate.now().minusDays(1))
