package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import java.time.LocalDate
import java.util.UUID
import kotlin.concurrent.fixedRateTimer
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes

private const val BEHOV = "RegistrertSomArbeidssøker"

internal class HentArbeidssøkerstatusJob(
    val rapidsConnection: RapidsConnection,
    val personRepository: PersonRepository,
) {
    private val logger = KotlinLogging.logger {}

    internal fun start() {
        fixedRateTimer(
            name = "Hent arbeidssøkerstatus",
            daemon = true,
            initialDelay = Random.nextInt(5).minutes.inWholeMilliseconds,
            period = 5.minutes.inWholeMilliseconds,
            action = {
                try {
                    personRepository
                        .hentPersonerUtenArbeidssøkerstatus()
                        .also { logger.info { "Henter arbeidssøkerstatus for ${it.size} personer" } }
                        .forEach { person ->
                            beOmArbeidssøkerstatus(
                                person.ident,
                                person.hendelser.last().referanseId,
                                person.hendelser.last().id,
                            )
                        }
                } catch (e: Exception) {
                    logger.warn(e) { "Feil ved henting av arbeidssøkerstatus" }
                }
            },
        )
    }

    internal fun beOmArbeidssøkerstatus(
        ident: String,
        søknadsId: String,
        hendelseId: UUID,
    ) {
        JsonMessage
            .newMessage(
                mapOf(
                    "@event_name" to "behov",
                    "@behov" to listOf(BEHOV),
                    "ident" to ident,
                    "søknadId" to søknadsId,
                    "behandlingId" to hendelseId.toString(),
                    "gjelderDato" to LocalDate.now(),
                    "@behovId" to UUID.randomUUID().toString(),
                    "RegistrertSomArbeidssøker" to
                        mapOf(
                            "Virkningsdato" to LocalDate.now(),
                        ),
                ),
            ).run { rapidsConnection.publish(this.toJson()) }
    }
}
