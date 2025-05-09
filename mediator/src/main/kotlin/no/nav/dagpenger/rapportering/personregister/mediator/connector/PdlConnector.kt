package no.nav.dagpenger.rapportering.personregister.mediator.connector

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import mu.KotlinLogging
import no.nav.dagpenger.pdl.PDLIdentliste
import no.nav.dagpenger.pdl.PersonOppslag
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.pdlApiTokenProvider

private val logger = KotlinLogging.logger {}

class PdlConnector(
    private val personOppslag: PersonOppslag,
    private val tokenProvider: () -> String? = pdlApiTokenProvider,
) {
    private val pdlClient =
        HttpClient {
            expectSuccess = true
            install(DefaultRequest) {
                this.url(Configuration.pdlUrl)
                header("Content-Type", "application/json")
                header("TEMA", "DAG")
                header(
                    "behandlingsnummer",
                    "B286",
                ) // https://behandlingskatalog.intern.nav.no/process/purpose/DAGPENGER/486f1672-52ed-46fb-8d64-bda906ec1bc9
                accept(ContentType.Application.Json)
            }
        }

    suspend fun hentPersonTest(id: String): Pdl.Person? {
        val token = tokenProvider.invoke() ?: throw RuntimeException("Klarte ikke å hente token")
        logger.info { "Er token med? $token" }
        return pdlClient
            .request {
                header(HttpHeaders.Authorization, "Bearer $token")
                method = HttpMethod.Post
                setBody(PersonQuery(id).toJson().also { logger.info { "Forsøker å hente person med id $id fra PDL" } })
            }.bodyAsText()
            .let { body ->
                getWarnings(body).takeIf { it.isNotEmpty() }?.map { warnings ->
                    logger.warn { "Warnings ved henting av person fra PDL: $warnings" }
                }
                if (hasError(body)) {
                    throw PdlPersondataOppslagException(body)
                } else {
                    Pdl.Person.fromGraphQlJson(body)
                }
            }
    }

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

internal data class PersonQuery(
    val id: String,
) : GraphqlQuery(
        //language=Graphql
        query =
            """
            query(${'$'}ident: ID!) {
                hentPerson(ident: ${'$'}ident) {
                    navn {
                        fornavn,
                        mellomnavn,
                        etternavn
                    },
                    adressebeskyttelse{
                        gradering
                    }
                }
                hentIdenter(ident: ${'$'}ident, grupper: [AKTORID,FOLKEREGISTERIDENT]) {
                    identer {
                        ident,
                        gruppe
                    }
                }                
            }
            """.trimIndent(),
        variables = mapOf("ident" to id),
    )

internal open class GraphqlQuery(
    val query: String,
    val variables: Any?,
) {
    fun toJson(): String = defaultObjectMapper.writeValueAsString(this)
}

private fun harGraphqlErrors(json: JsonNode) = json["errors"] != null && !json["errors"].isEmpty

private fun ukjentPersonIdent(node: JsonNode) = node["errors"]?.any { it["message"].asText() == "Fant ikke person" } ?: false

class Pdl {
    @JsonDeserialize(using = PersonDeserializer::class)
    data class Person(
        val navn: String,
        val aktørId: String,
        val fødselsnummer: String,
        val norskTilknytning: Boolean,
        val diskresjonskode: String?,
    ) {
        internal companion object {
            fun fromGraphQlJson(json: String): Person? = defaultObjectMapper.readValue(json, Person::class.java)
        }
    }

    object PersonDeserializer : JsonDeserializer<Person>() {
        internal fun JsonNode.aktørId() = this.ident("AKTORID")

        internal fun JsonNode.fødselsnummer() = this.ident("FOLKEREGISTERIDENT")

        internal fun JsonNode.norskTilknyting(): Boolean = findValue("gtLand")?.isNull ?: false

        internal fun JsonNode.diskresjonsKode(): String? = findValue("adressebeskyttelse").firstOrNull()?.path("gradering")?.asText(null)

        internal fun JsonNode.personNavn(): String =
            findValue("navn").first().let { node ->
                val fornavn = node.path("fornavn").asText()
                val mellomnavn = node.path("mellomnavn").asText("")
                val etternavn = node.path("etternavn").asText()

                when (mellomnavn.isEmpty()) {
                    true -> "$fornavn $etternavn"
                    else -> "$fornavn $mellomnavn $etternavn"
                }
            }

        private fun JsonNode.ident(type: String): String =
            findValue("identer").first { it.path("gruppe").asText() == type }.get("ident").asText()

        private fun JsonNode.harIdent(type: String): Boolean = findValue("identer").map { it["gruppe"].asText() }.contains(type)

        override fun deserialize(
            p: JsonParser,
            ctxt: DeserializationContext?,
        ): Person? {
            val node: JsonNode = p.readValueAsTree()

            return kotlin
                .runCatching {
                    Person(
                        navn = node.personNavn(),
                        aktørId = node.aktørId(),
                        fødselsnummer = node.fødselsnummer(),
                        norskTilknytning = node.norskTilknyting(),
                        diskresjonskode = node.diskresjonsKode(),
                    )
                }.fold(
                    onSuccess = {
                        it
                    },
                    onFailure = {
                        if (ukjentPersonIdent(node) || !node.harIdent("FOLKEREGISTERIDENT")) {
                            return null
                        } else {
                            logger.error(it) { "Feil ved deserialisering av PDL response: $node" }
                            throw it
                        }
                    },
                )
        }
    }
}

internal fun getWarnings(json: String): List<QueryWarning> {
    val node = jacksonObjectMapper().readTree(json)
    return node["extensions"]?.get("warnings")?.map {
        QueryWarning(
            id = it["id"].asText(),
            code = it["code"].asText(),
            message = it["message"].asText(),
            details =
                WarningDetails(
                    missing = it["details"]["missing"].map { missing -> missing.asText() },
                ),
        )
    } ?: emptyList()
}

data class QueryWarning(
    val id: String,
    val code: String,
    val message: String,
    val details: WarningDetails,
)

data class WarningDetails(
    val missing: List<String>,
)

internal class PdlPersondataOppslagException(
    s: String,
) : RuntimeException(s)

internal fun hasError(json: String): Boolean {
    val j = jacksonObjectMapper().readTree(json)
    return (harGraphqlErrors(j) && !ukjentPersonIdent(j))
}
