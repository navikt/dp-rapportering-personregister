package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import no.nav.dagpenger.rapportering.personregister.mediator.melding.Vedtakstype
import java.time.LocalDateTime

data class VedtakHendelse(
    val fnr: String,
    val vedtakId: String,
    val sakId: String,
    val fattet: LocalDateTime,
    val fraDato: LocalDateTime,
    val tilDato: LocalDateTime?,
    val status: Vedtakstype,
)
