package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.ktor.http.HttpHeaders
import no.nav.dagpenger.pdl.PDLIdentliste
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration

internal class PdlConnector(
    private val personOppslag: PersonOppslag,
    private val tokenProvider: (token: String, audience: String) -> String = { s: String, a: String ->
        Configuration.tokenXClient.tokenExchange(s, a).accessToken ?: throw RuntimeException("Fant ikke token")
    },
    private val pdlAudience: String = Configuration.pdlAudience,
) {
    suspend fun hentPerson(
        ident: String,
        subjectToken: String,
    ): Person {
        val pdlPerson =
            personOppslag
                .hentPerson(
                    ident,
                    mapOf(
                        HttpHeaders.Authorization to "Bearer ${tokenProvider.invoke(subjectToken, pdlAudience)}",
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

    fun hentIdenter(
        ident: String,
        subjectToken: String,
    ): PDLIdentliste {
        return personOppslag
            .hentIdenter(
                ident,
                listOf("NPID", "AKTORID", "FOLKEREGISTERIDENT"),
                true,
                mapOf(
                    HttpHeaders.Authorization to "Bearer ${tokenProvider.invoke(subjectToken, pdlAudience)}",
                    // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                    "behandlingsnummer" to "B286",
                ),
            )
    }
}
