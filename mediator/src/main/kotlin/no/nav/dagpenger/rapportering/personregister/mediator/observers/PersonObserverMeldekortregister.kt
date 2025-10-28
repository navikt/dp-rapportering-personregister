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
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
        skalMigreres: Boolean,
    ) {
        logger.info { "Sender Start-melding til Meldekortregister for person" }
        sikkerlogg.info { "Sender Start-melding til Meldekortregister for person ${person.ident}" }

        val message =
            JsonMessage.newMessage(
                "meldekortoppretting",
                buildMap {
                    put(
                        "personId",
                        personRepository.hentPersonId(person.ident) ?: throw IllegalArgumentException("Finner ikke personId"),
                    )
                    put("ident", person.ident)
                    put("fraOgMed", fraOgMed)
                    tilOgMed?.let { put("tilOgMed", it) }
                    put("handling", "START")
                    put("referanseId", UUID.randomUUID().toString())
                    put("skalMigreres", skalMigreres)
                },
            )

        sikkerlogg.info { "Sender Start-melding til Meldekortregister: ${message.toJson()}" }
        getRapidsConnection().publish(person.ident, message.toJson())
    }

    override fun sendStoppMeldingTilMeldekortregister(
        person: Person,
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
    ) {
        logger.info { "Sender Stopp-melding til Meldekortregister for person" }
        sikkerlogg.info { "Sender Stopp-melding til Meldekortregister for person ${person.ident}" }

        val message =
            JsonMessage.newMessage(
                "meldekortoppretting",
                buildMap {
                    put(
                        "personId",
                        personRepository.hentPersonId(person.ident) ?: throw IllegalArgumentException("Finner ikke personId"),
                    )
                    put("ident", person.ident)
                    put("fraOgMed", fraOgMed)
                    tilOgMed?.let { put("tilOgMed", it) }
                    put("handling", "STOPP")
                    put("referanseId", UUID.randomUUID().toString())
                    put("skalMigreres", false)
                },
            )

        sikkerlogg.info { "Sender Stopp-melding til Meldekortregister: ${message.toJson()}" }
        getRapidsConnection().publish(person.ident, message.toJson())
    }
}
