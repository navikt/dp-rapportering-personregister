package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalDate

class PersonObserverMeldekortregister : PersonObserver {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    override fun sendStartMeldingTilMeldekortregister(person: Person) {
        logger.info("Sender Start-melding til Meldekortregister for person")
        sikkerlogg.info("Sender Start-melding til Meldekortregister for person ${person.ident}")

        val message =
            JsonMessage.newMessage(
                "meldekortoppretting",
                mapOf(
                    "ident" to person.ident,
                    "dato" to LocalDate.now(),
                    "handling" to "START",
                ),
            )
        getRapidsConnection().publish(person.ident, message.toJson())
    }
}
