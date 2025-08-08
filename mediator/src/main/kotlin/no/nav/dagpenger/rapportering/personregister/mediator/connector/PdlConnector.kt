package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.http.HttpHeaders
import mu.KotlinLogging
import no.nav.dagpenger.pdl.PDLPerson
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
            logger.error(e) { "Feil ved henting av identer fra PDL" }
            sikkerLogg.error(e) { "Feil ved henting av identer fra PDL for ident $ident" }
            emptyList()
        }

    suspend fun hentPerson(ident: String): PDLPerson? =
        try {
            personOppslag
                .hentPerson(
                    ident,
                    mapOf(
                        HttpHeaders.Authorization to
                            "Bearer ${tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")}",
                        // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                        "behandlingsnummer" to "B286",
                    ),
                )
        } catch (e: Exception) {
            logger.error(e) { "Feil ved henting av person fra PDL" }
            sikkerLogg.error(e) { "Feil ved henting av person fra PDL for ident $ident" }
            null
        }

    companion object {
        private val logger = KotlinLogging.logger {}
        private val sikkerLogg = KotlinLogging.logger("tjenestekall.Pdl")
    }
}
