package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import java.util.UUID

enum class Handling { OVERTATT, FRASAGT }

data class ArbeidssøkerBeslutning(
    val ident: String,
    val periodeId: UUID,
    val handling: Handling,
    val referanseId: String,
    val begrunnelse: String,
)
