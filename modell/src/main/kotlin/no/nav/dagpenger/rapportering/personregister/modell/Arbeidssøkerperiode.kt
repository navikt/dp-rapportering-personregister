package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class Arbeidssøkerperiode(
    val periodeId: UUID,
    val ident: String,
    val startet: LocalDateTime,
    var avsluttet: LocalDateTime?,
    var overtattBekreftelse: Boolean?,
)

fun Arbeidssøkerperiode.aktiv(): Boolean = avsluttet == null

val List<Arbeidssøkerperiode>.gjeldende: Arbeidssøkerperiode?
    get() = this.firstOrNull { it.aktiv() }
