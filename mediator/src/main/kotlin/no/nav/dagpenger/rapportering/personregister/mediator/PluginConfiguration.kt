package no.nav.dagpenger.rapportering.personregister.mediator

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.AuthenticationConfig
import io.ktor.server.auth.jwt.jwt
import no.nav.dagpenger.rapportering.personregister.kafka.plugin.KafkaProducerPlugin
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.AuthFactory.azureAd
import no.nav.dagpenger.rapportering.personregister.mediator.api.auth.AuthFactory.tokenX

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
}
