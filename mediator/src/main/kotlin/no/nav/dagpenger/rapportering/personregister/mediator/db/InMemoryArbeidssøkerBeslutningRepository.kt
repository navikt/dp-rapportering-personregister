package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBeslutning

class InMemoryArbeidssøkerBeslutningRepository : ArbeidssøkerBeslutningRepository {
    private val beslutninger = mutableListOf<ArbeidssøkerBeslutning>()

    override fun hentBeslutning(id: String) = beslutninger.lastOrNull { it.periodeId.toString() == id }

    override fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning) {
        beslutninger.add(beslutning)
    }

    override fun hentBeslutninger(ident: String) = beslutninger.filter { it.ident == ident }
}
