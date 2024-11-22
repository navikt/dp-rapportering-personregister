package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.KafkaKonsument
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.Periode
import org.apache.kafka.clients.consumer.Consumer
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

private const val ARBEIDSSØKER_PERIODE_TOPIC = "paw.arbeidssokerperioder-v1"

class ArbeidssøkerperiodeMottak(
    konsument: Consumer<String, String>,
    private val personstatusMediator: PersonstatusMediator,
) : KafkaKonsument<String>(consumer = konsument, topic = ARBEIDSSØKER_PERIODE_TOPIC) {
    override fun stream() {
        stream { meldinger ->
            meldinger.forEach { melding ->
                logger.info {
                    logger.info { "Mottok melding om endring i arbeidssøkerperiode" }
                    try {
                        personstatusMediator
                            .behandle(
                                defaultObjectMapper
                                    .readValue(melding.value(), Periode::class.java)
                                    .tilHendelse(),
                            )
                    } catch (e: Exception) {
                        logger.error(e) { "Feil ved behandling av arbeidssøkerperiode" }
                    }
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {}
    }
}

private fun Periode.tilHendelse(): ArbeidssøkerHendelse =
    ArbeidssøkerHendelse(
        ident = this.identitetsnummer,
        periodeId = this.id,
        startDato = this.startet.tidspunkt.convertMillisToLocalDateTime(),
        sluttDato = this.avsluttet?.tidspunkt?.convertMillisToLocalDateTime(),
    )

fun Long.convertMillisToLocalDateTime(): LocalDateTime =
    LocalDateTime
        .ofInstant(Instant.ofEpochMilli(this), ZoneId.systemDefault())
