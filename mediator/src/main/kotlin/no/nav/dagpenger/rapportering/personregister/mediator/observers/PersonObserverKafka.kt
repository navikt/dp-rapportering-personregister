package no.nav.dagpenger.rapportering.personregister.mediator.observers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostrgesArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class PersonObserverKafka(
    private val producer: Producer<Long, PaaVegneAv>,
    private val arbeidssøkerConnector: ArbeidssøkerConnector,
    private val arbeidssøkerRepository: PostrgesArbeidssøkerRepository,
    private val frasiBekreftelseTopic: String,
) : PersonObserver {
    override fun frasiArbeidssøkerBekreftelse(person: Person): Unit =
        runBlocking {
            arbeidssøkerRepository
                .hentArbeidssøkerperioder(person.ident)
                .firstOrNull { it.avsluttet == null }
                ?.periodeId
                ?.let { periodeId ->
                    val recordKey = arbeidssøkerConnector.hentRecordKey(person.ident)
                    val record = ProducerRecord(frasiBekreftelseTopic, recordKey.key, PaaVegneAv(periodeId, DAGPENGER, Stopp()))
                    val metadata = producer.sendDeferred(record).await()
                    sikkerlogg.info {
                        "Sendt overtagelse av bekreftelse for periodeId $periodeId til arbeidssøkerregisteret. " +
                            "Metadata: topic=${metadata.topic()} (partition=${metadata.partition()}, offset=${metadata.offset()})"
                    }
                    arbeidssøkerRepository.oppdaterOvertagelse(periodeId, false)
                }
        }

    companion object {
        val sikkerlogg = KotlinLogging.logger("tjenestekall.PersonObserver")
    }
}
