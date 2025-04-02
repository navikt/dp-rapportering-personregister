package no.nav.dagpenger.rapportering.personregister.mediator.db

import java.util.UUID

enum class Handling { OVERTATT, FRASAGT }

data class ArbeidssøkerBeslutning(
    val ident: String,
    val periodeId: UUID,
    val handling: Handling,
    val referanseId: String,
    val begrunnelse: String,
)

interface ArbeidssøkerBeslutningRepository {
    fun hentBeslutning(periodeId: String): ArbeidssøkerBeslutning?

    fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning)

    fun hentBeslutninger(ident: String): List<ArbeidssøkerBeslutning>
}
