package no.nav.dagpenger.rapportering.personregister.mediator.service

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.ArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import java.util.UUID
import java.util.concurrent.TimeUnit.DAYS

class ArbeidssøkerService(
    private val personRepository: PersonRepository,
    private val arbeidssøkerRepository: ArbeidssøkerRepository,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val overtaBekreftelseKafkaProdusent: Producer<Long, PaaVegneAv>,
    private val overtaBekreftelseTopic: String,
) {
    fun sendOvertaBekreftelseBehov(
        ident: String,
        periodeId: UUID,
    ) {
        val recordKeyResponse = runBlocking { arbeidssøkerConnector.hentRecordKey(ident) }
        val record =
            ProducerRecord(
                overtaBekreftelseTopic,
                recordKeyResponse.key,
                PaaVegneAv(
                    periodeId,
                    DAGPENGER,
                    Start(
                        DAYS.toMillis(14),
                        DAYS.toMillis(8),
                    ),
                ),
            )
        val metadata = runBlocking { overtaBekreftelseKafkaProdusent.sendDeferred(record).await() }
        sikkerlogg.info {
            "Sendt overtagelse av bekreftelse for periodeId $periodeId til arbeidssøkerregisteret. " +
                "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
        }
        oppdaterOvertagelse(periodeId, true)
    }

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
        arbeidssøkerRepository.avsluttArbeidssøkerperiode(arbeidssøkerperiode.periodeId, arbeidssøkerperiode.avsluttet!!)
        arbeidssøkerRepository.oppdaterOvertagelse(arbeidssøkerperiode.periodeId, false)
    }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall.HentRapporteringperioder")
    }
}
