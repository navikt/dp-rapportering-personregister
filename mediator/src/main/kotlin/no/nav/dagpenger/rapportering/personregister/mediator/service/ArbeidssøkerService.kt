package no.nav.dagpenger.rapportering.personregister.mediator.service

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.util.UUID

class ArbeidssøkerService(
    private val personRepository: PersonRepository,
    private val arbeidssøkerRepository: ArbeidssøkerRepository,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
) {
    suspend fun hentSisteArbeidssøkerperiode(ident: String): Arbeidssøkerperiode? =
        arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident).firstOrNull()?.let {
            Arbeidssøkerperiode(
                periodeId = it.periodeId,
                startet = it.startet.tidspunkt,
                avsluttet = it.avsluttet?.tidspunkt,
                ident = ident,
                overtattBekreftelse = null,
            )
        }

    fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    ) = arbeidssøkerRepository.oppdaterOvertagelse(periodeId, overtattBekreftelse)

    fun erArbeidssøker(ident: String): Boolean = arbeidssøkerRepository.hentArbeidssøkerperioder(ident).any { it.avsluttet == null }

    fun finnesPerson(ident: String): Boolean = personRepository.finnesPerson(ident)

    fun hentLagredeArbeidssøkerperioder(ident: String) = arbeidssøkerRepository.hentArbeidssøkerperioder(ident)

    fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) =
        arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

    fun avsluttPeriodeOgOppdaterOvertagelse(arbeidssøkerperiode: Arbeidssøkerperiode) {
        arbeidssøkerRepository.avsluttArbeidssøkerperiode(
            arbeidssøkerperiode.periodeId,
            arbeidssøkerperiode.avsluttet!!,
        )
        arbeidssøkerRepository.oppdaterOvertagelse(arbeidssøkerperiode.periodeId, false)
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
