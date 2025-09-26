package no.nav.dagpenger.rapportering.personregister.mediator.api

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.MeldepliktMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldepliktConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.jobs.AktiverHendelserJob

fun Application.internalApi(
    meterRegistry: PrometheusMeterRegistry,
    aktiverHendelserJob: AktiverHendelserJob,
    personRepository: PersonRepository,
    personMediator: PersonMediator,
    meldepliktMediator: MeldepliktMediator,
    meldepliktConnector: MeldepliktConnector,
) {
    routing {
        get("/") {
            call.respond(HttpStatusCode.OK)
        }
        get("/isAlive") {
            call.respondText("Alive")
        }
        get("/isReady") {
            call.respondText("Ready")
        }
        get("/metrics") {
            call.respondText(meterRegistry.scrape())
        }
        get("/aktiver") {
            aktiverHendelserJob.aktivererHendelser(personRepository, personMediator, meldepliktMediator, meldepliktConnector)
            call.respond(HttpStatusCode.OK)
        }
    }
}
