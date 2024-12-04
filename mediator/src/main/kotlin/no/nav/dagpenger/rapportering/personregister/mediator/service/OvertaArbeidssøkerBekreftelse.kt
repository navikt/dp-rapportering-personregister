package no.nav.dagpenger.rapportering.personregister.mediator.service

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.produsent.KafkaProdusent
import java.util.concurrent.TimeUnit

data class OvertaArbeidssøkerBekreftelseMelding(
    val periodeId: String,
    val bekreftelsesLøsning: BekreftelsesLøsning = BekreftelsesLøsning.DAGPENGER,
    val start: Start = Start(),
) {
    enum class BekreftelsesLøsning {
        DAGPENGER,
    }

    data class Start(
        val intervalMS: Long = TimeUnit.DAYS.toMillis(14),
        val graceMS: Long = TimeUnit.DAYS.toMillis(8),
    )
}


class OvertaArbeidssøkerBekreftelse(
    private val kafkaProdusent: KafkaProdusent<OvertaArbeidssøkerBekreftelseMelding>,
) {
    private val logger = KotlinLogging.logger {}

    fun behandle(periodeId: String) {
        val melding = OvertaArbeidssøkerBekreftelseMelding(periodeId)
        try {
            kafkaProdusent.send(key = periodeId, value = melding)
            logger.info { "Successfully sent confirmation for periodeId=$periodeId" }
        } catch (e: Exception) {
            logger.error(e) { "Error while sending confirmation for periodeId=$periodeId" }
        }
    }
}
