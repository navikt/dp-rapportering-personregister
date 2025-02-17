package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerRepositoryFaker : ArbeidssøkerRepository {
    private val arbeidssøkerperioder = mutableListOf<Arbeidssøkerperiode>()

    override fun hentArbeidssøkerperioder(ident: String): List<Arbeidssøkerperiode> = arbeidssøkerperioder.filter { it.ident == ident }

    override fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) {
        arbeidssøkerperioder.add(arbeidssøkerperiode)
    }

    override fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    ) {
        hentPeriode(periodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(overtattBekreftelse = overtattBekreftelse)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $periodeId")
    }

    override fun avsluttArbeidssøkerperiode(
        periodeId: UUID,
        avsluttetDato: LocalDateTime,
    ) {
        hentPeriode(periodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(avsluttet = avsluttetDato)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $periodeId")
    }

    override fun oppdaterPeriodeId(
        ident: String,
        gammelPeriodeId: UUID,
        nyPeriodeId: UUID,
    ) {
        hentPeriode(gammelPeriodeId)
            .takeIf { it != null }
            ?.let { periode ->
                arbeidssøkerperioder.remove(periode)
                periode
                    .copy(periodeId = nyPeriodeId)
                    .let { arbeidssøkerperioder.add(it) }
            } ?: throw RuntimeException("Fant ikke periode med id $gammelPeriodeId")
    }

    private fun hentPeriode(periodeId: UUID) = arbeidssøkerperioder.find { it.periodeId == periodeId }
}
