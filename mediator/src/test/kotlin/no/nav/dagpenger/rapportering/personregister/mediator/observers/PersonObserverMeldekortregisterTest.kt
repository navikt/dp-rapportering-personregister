package no.nav.dagpenger.rapportering.personregister.mediator.observers

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class PersonObserverMeldekortregisterTest {
    val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

    @Test
    fun `kan sende og lagre Start-melding`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val startDato = LocalDateTime.now().minusDays(1)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStartMeldingTilMeldekortregister(person, startDato, null, true)

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "meldekortoppretting"
        message["personId"].asLong() shouldBe personId
        message["ident"].asText() shouldBe ident
        message["fraOgMed"].asLocalDateTime() shouldBe startDato
        message["tilOgMed"] shouldBe null
        message["harRett"].asBoolean() shouldBe true
        message["handling"].asText() shouldBe "START"
        message["referanseId"].asText() shouldNotBe null
        message["skalMigreres"].asBoolean() shouldBe true

        coVerify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                melding =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["@event_name"].asText() == "meldekortoppretting" &&
                                this["personId"].asLong() == personId &&
                                this["ident"].asText() == ident &&
                                this["fraOgMed"].asLocalDateTime() == startDato &&
                                this["tilOgMed"] == null &&
                                this["harRett"].asBoolean() &&
                                this["handling"].asText() == "START" &&
                                this["referanseId"] != null &&
                                this["skalMigreres"].asBoolean()
                        }
                    },
            )
        }
    }

    @Test
    fun `kan sende og lagre Start-melding med tilOgMed`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(1)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStartMeldingTilMeldekortregister(person, fraOgMed, tilOgMed, false)

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "meldekortoppretting"
        message["personId"].asLong() shouldBe personId
        message["ident"].asText() shouldBe ident
        message["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        message["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        message["harRett"].asBoolean() shouldBe true
        message["handling"].asText() shouldBe "START"
        message["referanseId"].asText() shouldNotBe null
        message["skalMigreres"].asBoolean() shouldBe false

        coVerify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                melding =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["@event_name"].asText() == "meldekortoppretting" &&
                                this["personId"].asLong() == personId &&
                                this["ident"].asText() == ident &&
                                this["fraOgMed"].asLocalDateTime() == fraOgMed &&
                                this["tilOgMed"].asLocalDateTime() == tilOgMed &&
                                this["harRett"].asBoolean() &&
                                this["handling"].asText() == "START" &&
                                this["referanseId"] != null &&
                                !this["skalMigreres"].asBoolean()
                        }
                    },
            )
        }
    }

    @Test
    fun `kan sende og lagre Stopp-melding med harRett true`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(2)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
            person = person,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            harRett = true,
        )

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "meldekortoppretting"
        message["personId"].asLong() shouldBe personId
        message["ident"].asText() shouldBe ident
        message["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        message["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        message["harRett"].asBoolean() shouldBe true
        message["handling"].asText() shouldBe "STOPP"
        message["referanseId"].asText() shouldNotBe null
        message["skalMigreres"].asBoolean() shouldBe false

        coVerify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                melding =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["@event_name"].asText() == "meldekortoppretting" &&
                                this["personId"].asLong() == personId &&
                                this["ident"].asText() == ident &&
                                this["fraOgMed"].asLocalDateTime() == fraOgMed &&
                                this["tilOgMed"].asLocalDateTime() == tilOgMed &&
                                this["harRett"].asBoolean() &&
                                this["handling"].asText() == "STOPP" &&
                                this["referanseId"] != null &&
                                !this["skalMigreres"].asBoolean()
                        }
                    },
            )
        }
    }

    @Test
    fun `kan sende og lagre Stopp-melding med harRett false`() {
        val testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid

        val personId = 1234L
        val ident = "12345678910"
        val person = Person(ident)
        val fraOgMed = LocalDateTime.now().minusDays(2)
        val tilOgMed = LocalDateTime.now().plusDays(10)

        val personRepository = mockk<PersonRepository>(relaxed = true)
        every { personRepository.hentPersonId(eq(ident)) } returns personId

        val personObserverMeldekortregister = PersonObserverMeldekortregister(personRepository, meldingerRepository)

        personObserverMeldekortregister.sendStoppMeldingTilMeldekortregister(
            person = person,
            fraOgMed = fraOgMed,
            tilOgMed = tilOgMed,
            harRett = false,
        )

        testRapid.inspektør.size shouldBe 1

        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "meldekortoppretting"
        message["personId"].asLong() shouldBe personId
        message["ident"].asText() shouldBe ident
        message["fraOgMed"].asLocalDateTime() shouldBe fraOgMed
        message["tilOgMed"].asLocalDateTime() shouldBe tilOgMed
        message["harRett"].asBoolean() shouldBe false
        message["handling"].asText() shouldBe "STOPP"
        message["referanseId"].asText() shouldNotBe null
        message["skalMigreres"].asBoolean() shouldBe false

        coVerify(exactly = 1) {
            meldingerRepository.lagreUtgåendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                melding =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["@event_name"].asText() == "meldekortoppretting" &&
                                this["personId"].asLong() == personId &&
                                this["ident"].asText() == ident &&
                                this["fraOgMed"].asLocalDateTime() == fraOgMed &&
                                this["tilOgMed"].asLocalDateTime() == tilOgMed &&
                                !this["harRett"].asBoolean() &&
                                this["handling"].asText() == "STOPP" &&
                                this["referanseId"] != null &&
                                !this["skalMigreres"].asBoolean()
                        }
                    },
            )
        }
    }
}
