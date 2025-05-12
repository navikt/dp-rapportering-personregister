package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.http.HttpHeaders
import no.nav.dagpenger.pdl.PDLIdentliste
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.pdlApiTokenProvider

class PdlConnector(
    private val personOppslag: PersonOppslag,
    private val tokenProvider: () -> String? = pdlApiTokenProvider,
) {
    suspend fun hentPerson(ident: String): Person {
        val token = tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")
        val pdlPerson =
            personOppslag.hentPerson(
                ident,
                mapOf(
                    HttpHeaders.Authorization to "Bearer $token",
                    // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                    "behandlingsnummer" to "B286",
                ),
            )

        return Person(
            forNavn = pdlPerson.fornavn,
            mellomNavn = pdlPerson.mellomnavn ?: "",
            etterNavn = pdlPerson.etternavn,
            fødselsDato = pdlPerson.fodselsdato,
            ident = ident,
        )
    }

    fun hentIdenter(ident: String): PDLIdentliste =
        personOppslag
            .hentIdenter(
                ident,
                listOf("NPID", "AKTORID", "FOLKEREGISTERIDENT"),
                true,
                mapOf(
                    HttpHeaders.Authorization to "Bearer ${tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")}",
                    // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                    "behandlingsnummer" to "B286",
                ),
            )
}
