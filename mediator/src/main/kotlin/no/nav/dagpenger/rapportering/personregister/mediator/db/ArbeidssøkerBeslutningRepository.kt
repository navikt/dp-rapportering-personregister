package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning

interface ArbeidssøkerBeslutningRepository {
    fun hentBeslutning(periodeId: String): ArbeidssøkerBeslutning?

    fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning)

    fun hentBeslutninger(ident: String): List<ArbeidssøkerBeslutning>
}
