package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.time.LocalDateTime

data class VedtakHendelse(
    val ident: String,
    val referanseId: String,
    val dato: LocalDateTime,
    val status: Status,
    val kildesystem: Kildesystem = Kildesystem.Arena,
)
