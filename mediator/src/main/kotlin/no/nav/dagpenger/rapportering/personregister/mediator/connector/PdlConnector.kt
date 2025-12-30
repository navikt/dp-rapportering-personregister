package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.HttpHeaders
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.pdlApiTokenProvider
import no.nav.dagpenger.rapportering.personregister.modell.Ident

class PdlConnector(
    private val personOppslag: PersonOppslag,
    private val tokenProvider: () -> String? = pdlApiTokenProvider,
) {
    fun hentIdenter(ident: String): List<Ident> =
        try {
            personOppslag
                .hentIdenter(
                    ident,
                    listOf("NPID", "AKTORID", "FOLKEREGISTERIDENT"),
                    true,
                    mapOf(
                        HttpHeaders.Authorization to
                            "Bearer ${tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")}",
                        // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                        "behandlingsnummer" to "B286",
                    ),
                ).identer
                .map { pdlIdent ->
                    Ident(
                        ident = pdlIdent.ident,
                        gruppe = Ident.IdentGruppe.valueOf(pdlIdent.gruppe.toString()),
                        historisk = pdlIdent.historisk,
                    )
                }
        } catch (e: Exception) {
            logger.warn(e) { "Feil ved henting av identer fra PDL" }
            sikkerLogg.warn(e) { "Feil ved henting av identer fra PDL for ident $ident" }
            emptyList()
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall")
    }
}
