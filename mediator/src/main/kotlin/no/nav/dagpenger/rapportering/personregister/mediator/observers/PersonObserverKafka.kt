package no.nav.dagpenger.rapportering.personregister.mediator.observers

import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.kafka.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostrgesArbeidssøkerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService.Companion.sikkerlogg
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning.DAGPENGER
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Stopp
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord

class PersonObserverKafka(
    val producer: Producer<Long, PaaVegneAv>,
    val arbeidssøkerConnector: ArbeidssøkerConnector,
    val arbeidssøkerRepository: PostrgesArbeidssøkerRepository,
) : PersonObserver {
    val topic = Configuration.config.getValue("OVERTA_BEKREFTELSE_TOPIC") // TODO: Fix denne

    override fun frasiArbeidssøkerBekreftelse(person: Person) =
        runBlocking {
            val periodeId =
                arbeidssøkerRepository
                    .hentArbeidssøkerperioder(
                        person.ident,
                    ).filter { it.avsluttet == null }
                    .firstOrNull()
                    ?.periodeId
            if (periodeId != null) {
                val recordKey = arbeidssøkerConnector.hentRecordKey(person.ident)
                val record = ProducerRecord(topic, recordKey.key, PaaVegneAv(periodeId, DAGPENGER, Stopp()))
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
