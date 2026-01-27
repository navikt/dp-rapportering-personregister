package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import java.time.LocalDateTime

class PersonObserverMeldekortregister(
    private val personRepository: PersonRepository,
) : PersonObserver {
    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerlogg = KotlinLogging.logger("tjenestekall")
    }

    override fun sendStartMeldingTilMeldekortregister(
        person: Person,
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
        skalMigreres: Boolean,
    ) {
        logger.info { "Sender Start-melding til Meldekortregister for person" }
        sikkerlogg.info { "Sender Start-melding til Meldekortregister for person ${person.ident}" }

        try {
            val message =
                JsonMessage.newMessage(
                    "meldekortoppretting",
                    buildMap {
                        put("personId", person.hentPersonId())
                        put("ident", person.ident)
                        put("fraOgMed", fraOgMed)
                        tilOgMed?.let { put("tilOgMed", it) }
                        put("handling", "START")
                        put("referanseId", UUIDv7.newUuid().toString())
                        put("skalMigreres", skalMigreres)
                    },
                )

            sikkerlogg.info { "Sender Start-melding til Meldekortregister: ${message.toJson()}" }
            getRapidsConnection().publish(person.ident, message.toJson())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved sending av Start-melding til Meldekortregister" }
            sikkerlogg.error(e) { "Feil ved sending av Start-melding til Meldekortregister for person ${person.ident}" }
            throw e
        }
    }

    override fun sendStoppMeldingTilMeldekortregister(
        person: Person,
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
    ) {
        logger.info { "Sender Stopp-melding til Meldekortregister for person" }
        sikkerlogg.info { "Sender Stopp-melding til Meldekortregister for person ${person.ident}" }

        try {
            val message =
                JsonMessage.newMessage(
                    "meldekortoppretting",
                    buildMap {
                        put("personId", person.hentPersonId())
                        put("ident", person.ident)
                        put("fraOgMed", fraOgMed)
                        tilOgMed?.let { put("tilOgMed", it) }
                        put("handling", "STOPP")
                        put("referanseId", UUIDv7.newUuid().toString())
                        put("skalMigreres", false)
                    },
                )

            sikkerlogg.info { "Sender Stopp-melding til Meldekortregister: ${message.toJson()}" }
            getRapidsConnection().publish(person.ident, message.toJson())
        } catch (e: Exception) {
            logger.error(e) { "Feil ved sending av Stopp-melding til Meldekortregister" }
            sikkerlogg.error(e) { "Feil ved sending av Stopp-melding til Meldekortregister for person ${person.ident}" }
            throw e
        }
    }

    private fun Person.hentPersonId(): Long =
        personRepository.hentPersonId(ident)
            ?: throw IllegalArgumentException("Finner ikke personId for ident $ident")
}
