package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

data class Hendelse(
    val id: UUID = UUID.randomUUID(),
    val ident: String,
    val referanseId: String,
    val dato: LocalDateTime,
    val status: Status,
    val kilde: Kildesystem,
)

enum class Kildesystem {
    DpSoknad,
    Arena,
}
