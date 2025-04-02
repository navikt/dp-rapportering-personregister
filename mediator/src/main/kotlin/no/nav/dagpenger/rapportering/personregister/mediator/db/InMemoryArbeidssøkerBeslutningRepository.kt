package no.nav.dagpenger.rapportering.personregister.mediator.db

class InMemoryArbeidssøkerBeslutningRepository : ArbeidssøkerBeslutningRepository {
    private val beslutninger = mutableListOf<ArbeidssøkerBeslutning>()

    override fun hentBeslutning(periodeId: String) = beslutninger.lastOrNull { it.periodeId.toString() == periodeId }

    override fun lagreBeslutning(beslutning: ArbeidssøkerBeslutning) {
        beslutninger.add(beslutning)
    }

    override fun hentBeslutninger(ident: String) = beslutninger.filter { it.ident == ident }
}
