package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.time.LocalDateTime
import java.util.UUID

interface ArbeidssøkerRepository {
    fun hentArbeidssøkerperioder(ident: String): List<Arbeidssøkerperiode>

    fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode)

    fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    )

    fun avsluttArbeidssøkerperiode(
        periodeId: UUID,
        avsluttetDato: LocalDateTime,
    )

    fun oppdaterPeriodeId(
        ident: String,
        gammelPeriodeId: UUID,
        nyPeriodeId: UUID,
    )
}
