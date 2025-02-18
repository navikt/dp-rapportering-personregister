package no.nav.dagpenger.rapportering.personregister.mediator

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaConsumerPlugin
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaProducerPlugin
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.AuthFactory.tokenX
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode

fun Application.pluginConfiguration(
    meterRegistry: PrometheusMeterRegistry,
    kafkaContext: KafkaContext,
    auth: AuthenticationConfig.() -> Unit = {
        jwt("tokenX") { tokenX() }
    },
) {
    println("Installing plugins")
    install(Authentication) {
        println("Installing authentication")
        auth()
    }

    install(ContentNegotiation) {
        println("Installing content negotiation")
        jackson {
            registerModule(JavaTimeModule())
            registerModule(
                KotlinModule
                    .Builder()
                    .configure(KotlinFeature.NullToEmptyCollection, true)
                    .configure(KotlinFeature.NullToEmptyMap, true)
                    .configure(KotlinFeature.NullIsSameAsDefault, true)
                    .configure(KotlinFeature.SingletonSupport, true)
                    .configure(KotlinFeature.StrictNullChecks, false)
                    .build(),
            )
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            enable(SerializationFeature.INDENT_OUTPUT)
        }
    }

    install(MicrometerMetrics) {
        println("Installing micrometer metrics")
        registry = meterRegistry
        meterBinders =
            listOf(
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics(),
            )
    }
    println("Installing kafka producer plugin")
    install(KafkaProducerPlugin) {
        println("Installing kafka producer plugin")
        kafkaProducers = listOf(kafkaContext.overtaBekreftelseKafkaProdusent)
    }
    println("Installing kafka consumer plugin")
    install(KafkaConsumerPlugin<Long, Periode>("Arbeidssøkerperioder")) {
        println("Installing kafka consumer plugin")
        this.consumeFunction = kafkaContext.arbeidssøkerMediator::behandle
        // this.errorFunction = kafkaContext.kafkaConsumerExceptionHandler::handleException
        this.kafkaConsumer = kafkaContext.arbeidssøkerperioderKafkaConsumer
        this.kafkaTopics = listOf(kafkaContext.arbeidssøkerperioderTopic)
    }
}
