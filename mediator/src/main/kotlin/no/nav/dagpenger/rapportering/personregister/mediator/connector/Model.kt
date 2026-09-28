package no.nav.dagpenger.rapportering.personregister.mediator.connector

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.UUID

data class SisteFastsattMeldedatoRequest(
    val ident: String,
    val årsakTilUtmelding: Arbeidssøkerperiode.ÅrsakTilUtmelding,
)

data class SisteFastsattMeldedatoResponse(
    val fastsattMeldedato: LocalDate?,
)

data class ArbeidssøkerperiodeRequestBody(
    val identitetsnummer: String,
    val type: String = "IDENTITETSNUMMER",
)

data class RecordKeyRequestBody(
    val ident: String,
)

data class RecordKeyResponse(
    val key: Long,
)

data class ArbeidssøkerperiodeResponse(
    val periodeId: UUID,
    val startet: OffsetDateTime,
    val avsluttet: OffsetDateTime?,
    val hendelser: List<HendelseResponse>,
)

data class HendelseResponse(
    val type: String,
    val tidspunkt: OffsetDateTime,
)
