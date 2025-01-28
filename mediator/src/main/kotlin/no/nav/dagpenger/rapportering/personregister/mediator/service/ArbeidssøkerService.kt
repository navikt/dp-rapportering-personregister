package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.Arbeidssøkerstatus
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.BehovType.OvertaBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import java.util.UUID

class ArbeidssøkerService(
    private val rapidsConnection: RapidsConnection,
    private val personRepository: PersonRepository,
    private val arbeidssøkerRepository: ArbeidssøkerRepository,
) {
    fun sendOvertaBekreftelseBehov(
        ident: String,
        periodeId: UUID,
    ) {
        publiserBehov(OvertaBekreftelseBehov(ident, periodeId.toString()))
    }

    fun sendArbeidssøkerBehov(ident: String) {
        publiserBehov(ArbeidssøkerstatusBehov(ident))
        sikkerlogg.info { "Publiserte behov for arbeidssøkerstatus for ident $ident" }
    }

    fun oppdaterOvertagelse(
        periodeId: UUID,
        overtattBekreftelse: Boolean,
    ) = arbeidssøkerRepository.oppdaterOvertagelse(periodeId, overtattBekreftelse)

    fun erArbeidssøker(ident: String): Boolean = arbeidssøkerRepository.hentArbeidssøkerperioder(ident).any { it.avsluttet == null }

    fun finnesPerson(ident: String): Boolean = personRepository.finnesPerson(ident)

    fun hentArbeidssøkerperioder(ident: String) = arbeidssøkerRepository.hentArbeidssøkerperioder(ident)

    fun lagreArbeidssøkerperiode(arbeidssøkerperiode: Arbeidssøkerperiode) =
        arbeidssøkerRepository.lagreArbeidssøkerperiode(arbeidssøkerperiode)

    fun avsluttPeriodeOgOppdaterOvertagelse(arbeidssøkerperiode: Arbeidssøkerperiode) {
        arbeidssøkerRepository.avsluttArbeidssøkerperiode(arbeidssøkerperiode.periodeId, arbeidssøkerperiode.avsluttet!!)
        arbeidssøkerRepository.oppdaterOvertagelse(arbeidssøkerperiode.periodeId, false)
    }

    private fun publiserBehov(behov: Behovmelding) {
        sikkerlogg.info { "Publiserer behov ${behov.behovType} for ident ${behov.ident}" }
        val melding = behov.tilMelding().toJson()
        sikkerlogg.info { "Publiserte melding: $melding" }
        rapidsConnection.publish(melding)
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}

sealed class Behovmelding(
    open val ident: String,
    val behovType: BehovType,
) {
    abstract fun tilMelding(): JsonMessage
}

data class ArbeidssøkerstatusBehov(
    override val ident: String,
) : Behovmelding(ident, Arbeidssøkerstatus) {
    override fun tilMelding(): JsonMessage =
        JsonMessage.newMessage(
            "behov_arbeidssokerstatus",
            mutableMapOf(
                "@behov" to listOf(behovType.name),
                "ident" to ident,
            ),
        )
}

data class OvertaBekreftelseBehov(
    override val ident: String,
    val periodeId: String,
) : Behovmelding(ident, OvertaBekreftelse) {
    override fun tilMelding(): JsonMessage =
        JsonMessage.newMessage(
            "behov_arbeidssokerstatus",
            mutableMapOf(
                "@behov" to listOf(behovType.name),
                "ident" to ident,
                "periodeId" to periodeId,
            ),
        )
}
