package no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker

import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerperiodeResponse(
    val periodeId: UUID,
    val startet: MetadataResponse,
    val avsluttet: MetadataResponse?,
)

data class MetadataResponse(
    val tidspunkt: LocalDateTime,
    val utfoertAv: BrukerResponse,
    val kilde: String,
    val aarsak: String,
    val tidspunktFraKilde: TidspunktFraKildeResponse?,
)

data class BrukerResponse(
    val type: String,
    val id: String,
)

data class TidspunktFraKildeResponse(
    val tidspunkt: LocalDateTime,
    val avviksType: String,
)
