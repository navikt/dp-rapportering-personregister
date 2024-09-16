package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class Hendelse(
    val id: UUID = UUID.randomUUID(),
    val personId: String,
    val referanseId: String,
    val mottatt: LocalDateTime,
    val beskrivelse: String,
    val kilde: Kildesystem,
)

enum class Kildesystem {
    DpSoknad,
    Arena,
}
