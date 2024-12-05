package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.Periode
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.konsument.KafkaConsumerImpl
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.KafkaConsumer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val ARBEIDSSØKER_PERIODE_TOPIC = "paw.arbeidssokerperioder-v1"

class ArbeidssøkerperiodeMottak(
    kafkaConsumer: KafkaConsumer<String, String>,
    private val personstatusMediator: PersonstatusMediator,
) : KafkaConsumerImpl<String>(
        kafkaConsumer = kafkaConsumer,
        topic = ARBEIDSSØKER_PERIODE_TOPIC,
    ) {
    companion object {
        private val logger = KotlinLogging.logger {}
    }

    override fun process(record: ConsumerRecord<String, String>) {
        logger.info { "Mottok melding om endring i arbeidssøkerperiode" }
        runCatching {
            personstatusMediator.behandle(record.tilHendelse())
        }.onFailure { e ->
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
        }
    }
}

private fun ConsumerRecord<String, String>.tilHendelse(): ArbeidssøkerHendelse =
    defaultObjectMapper.readValue(value(), Periode::class.java).let { periode ->
        ArbeidssøkerHendelse(
            ident = periode.identitetsnummer,
            periodeId = periode.id,
            startDato = periode.startet.tidspunkt.toLocalDateTime(),
            sluttDato = periode.avsluttet?.tidspunkt?.toLocalDateTime(),
        )
    }

private fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDateTime()
