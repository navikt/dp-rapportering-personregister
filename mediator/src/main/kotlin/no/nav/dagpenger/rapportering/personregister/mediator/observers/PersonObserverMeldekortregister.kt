package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalDateTime
import java.util.UUID

class PersonObserverMeldekortregister(
    private val personRepository: PersonRepository,
) : PersonObserver {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    override fun sendStartMeldingTilMeldekortregister(
        person: Person,
        startDato: LocalDateTime,
    ) {
        logger.info("Sender Start-melding til Meldekortregister for person")
        sikkerlogg.info("Sender Start-melding til Meldekortregister for person ${person.ident}")

        val message =
            JsonMessage.newMessage(
                "meldekortoppretting",
                mapOf(
                    "personId" to (personRepository.hentPersonId(person.ident) ?: 0),
                    "ident" to person.ident,
                    "dato" to startDato,
                    "handling" to "START",
                    "referanseId" to UUID.randomUUID().toString(),
                ),
            )

        sikkerlogg.info { "Sender Start-melding til Meldekortregister: ${message.toJson()}" }
        getRapidsConnection().publish(person.ident, message.toJson())
    }
}
