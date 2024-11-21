package no.nav.dagpenger.rapportering.personregister.mediator.kafka

import mu.KotlinLogging
import org.slf4j.MDC
import java.util.UUID
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

fun <T> startLytting(konsument: KafkaKonsument<in T>) {
    Thread {
        MDC.put("CORRELATION_ID", UUID.randomUUID().toString())
        try {
            logger.info("Starter å lytte på ${konsument.topic}")
            konsument.stream()
        } catch (e: Exception) {
            logger.error("App avsluttet med en feil", e)
            exitProcess(1)
        }
    }.start()
}
