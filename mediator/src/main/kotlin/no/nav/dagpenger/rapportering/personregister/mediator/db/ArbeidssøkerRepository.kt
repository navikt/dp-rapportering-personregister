package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDateTime
import java.util.UUID

interface ArbeidssøkerRepository {
    fun hentArbeidssøkerperioder(ident: String): List<Arbeidssøkerperiode>

    @Throws(RuntimeException::class, IllegalStateException::class)
    fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode)

    @Throws(RuntimeException::class)
    fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    )

    @Throws(RuntimeException::class)
    fun avsluttArbeidssøkerperiode(
        periodeId: UUID,
        avsluttetDato: LocalDateTime,
    )

    @Throws(RuntimeException::class)
    fun oppdaterPeriodeId(
        ident: String,
        gammelPeriodeId: UUID,
        nyPeriodeId: UUID,
    )
}
