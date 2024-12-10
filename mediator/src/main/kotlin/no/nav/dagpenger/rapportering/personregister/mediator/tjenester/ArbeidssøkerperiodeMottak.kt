package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.Periode
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.consumer.KafkaMessageHandler
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ArbeidssøkerperiodeMottak(
    private val personStatusMediator: PersonstatusMediator,
) : KafkaMessageHandler {
    override val topic: String = "paw.arbeidssokerperioder-v1"

    override fun onMessage(record: ConsumerRecord<String, String>) {
        logger.info { "Mottok melding om endring i arbeidssøkerperiode: ${record.value()}" }
        try {
            val hendelse = record.tilHendelse()
            personStatusMediator.behandle(hendelse)
        } catch (e: Exception) {
            logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
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
