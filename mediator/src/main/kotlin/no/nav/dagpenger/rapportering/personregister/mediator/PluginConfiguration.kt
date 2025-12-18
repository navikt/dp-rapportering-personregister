package no.nav.dagpenger.rapportering.personregister.mediator

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaConsumerPlugin
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaProducerPlugin
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.AuthFactory.azureAd
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.AuthFactory.tokenX
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv

fun Application.pluginConfiguration(
    kafkaContext: KafkaContext,
    auth: AuthenticationConfig.() -> Unit = {
        jwt("tokenX") { tokenX() }
        jwt("azureAd") { azureAd() }
    },
) {
    install(Authentication) {
        auth()
    }

    install(KafkaProducerPlugin) {
        kafkaProducers = listOf(kafkaContext.bekreftelsePåVegneAvKafkaProdusent)
    }

    install(KafkaConsumerPlugin<Long, Periode>("Arbeidssøkerperioder")) {
        this.consumeFunction = kafkaContext.arbeidssøkerMottak::consume
        // this.errorFunction = kafkaContext.kafkaConsumerExceptionHandler::handleException
        this.kafkaConsumer = kafkaContext.arbeidssøkerperioderKafkaConsumer
        this.kafkaTopics = listOf(kafkaContext.arbeidssøkerperioderTopic)
    }

    install(KafkaConsumerPlugin<Long, PaaVegneAv>("BekreftelsePåVegneAv")) {
        this.consumeFunction = kafkaContext.påVegneAvMottak::consume
        this.kafkaConsumer = kafkaContext.bekreftelsePåVegneAvKafkaConsumer
        this.kafkaTopics = listOf(kafkaContext.påVegneAvTopic)
    }
}
