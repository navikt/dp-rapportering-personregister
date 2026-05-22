package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode.ÅrsakTilUtmelding
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ArbeidssøkerServiceTest {
    private val rapidsConnection = TestRapid()
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>(relaxed = true)

    private val arbeidssøkerService =
        ArbeidssøkerService(
            personRepository = personRepository,
            arbeidssøkerConnector = arbeidssøkerConnector,
            meldekortregisterConnector = meldekortregisterConnector,
            rapidsConnection = { rapidsConnection },
        )

    private val ident = "12345678901"
    private val periodeId = UUIDv7.newUuid()
    private val startet = LocalDateTime.now().minusWeeks(3)
    private val avregistrertTidspunkt = LocalDateTime.now().minusDays(1)

    @Test
    fun `kan hente siste arbeidssøkerperiode`() {
        val response = arbeidssøkerResponse(UUIDv7.newUuid())
        coEvery { arbeidssøkerConnector.hentSisteArbeidssøkerperiode(ident) } returns listOf(response)

        val periode = runBlocking { arbeidssøkerService.hentSisteArbeidssøkerperiode(ident) }

        val startetUTC =
            periode
                ?.startet
                ?.atZone(ZONE_ID)
                ?.withZoneSameInstant(ZoneId.of("UTC"))
                ?.toOffsetDateTime()

        response.periodeId shouldBe periode?.periodeId
        response.startet.tidspunkt shouldBe startetUTC
    }

    @Test
    fun `publiserer melding med årsak fra repository og fastsattMeldedato fra meldekort når ansvarligSystem er DP`() {
        runBlocking {
            val forventetMeldedato = LocalDate.now().minusDays(1)
            val person = person(ansvarligSystem = AnsvarligSystem.DP)

            every { personRepository.hentPerson(ident) } returns person
            every { personRepository.hentÅrsakTilUtmelding(periodeId, ident) } returns ÅrsakTilUtmelding.UTMELDT_PÅ_MELDEKORT
            coEvery { meldekortregisterConnector.hentSisteFastsattMeldedato(ident) } returns forventetMeldedato

            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())

            rapidsConnection.inspektør.size shouldBe 1
            with(rapidsConnection.inspektør.message(0)) {
                this["@event_name"].asText() shouldBe "avsluttet_arbeidssokerperiode"
                this["ident"].asText() shouldBe ident
                this["periodeId"].asText() shouldBe periodeId.toString()
                this["avregistrertTidspunkt"].asLocalDateTime() shouldBe avregistrertTidspunkt
                this["årsak"].asText() shouldBe ÅrsakTilUtmelding.UTMELDT_PÅ_MELDEKORT.dbValue
                LocalDate.parse(this["fastsattMeldedato"].asText()) shouldBe forventetMeldedato
            }
        }
    }

    @Test
    fun `defaulter årsak til UTMELDT_I_ARBEIDSSØKERREGISTERET når repository ikke har lagret årsak`() {
        runBlocking {
            val person = person(ansvarligSystem = AnsvarligSystem.DP)
            every { personRepository.hentPerson(ident) } returns person
            every { personRepository.hentÅrsakTilUtmelding(periodeId, ident) } returns null
            coEvery { meldekortregisterConnector.hentSisteFastsattMeldedato(ident) } returns null

            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())

            rapidsConnection.inspektør.message(0)["årsak"].asText() shouldBe
                ÅrsakTilUtmelding.UTMELDT_I_ARBEIDSSØKERREGISTERET.dbValue
        }
    }

    @Test
    fun `utelater fastsattMeldedato når ingen innsendte meldekort finnes`() {
        runBlocking {
            val person = person(ansvarligSystem = AnsvarligSystem.DP)
            every { personRepository.hentPerson(ident) } returns person
            every { personRepository.hentÅrsakTilUtmelding(periodeId, ident) } returns null
            coEvery { meldekortregisterConnector.hentSisteFastsattMeldedato(ident) } returns null

            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())

            rapidsConnection.inspektør.message(0).has("fastsattMeldedato") shouldBe false
        }
    }

    @Test
    fun `publiserer ikke melding når ansvarligSystem ikke er DP`() {
        runBlocking {
            val person = person(ansvarligSystem = AnsvarligSystem.ARENA)
            every { personRepository.hentPerson(ident) } returns person

            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())

            rapidsConnection.inspektør.size shouldBe 0
        }
    }

    @Test
    fun `publiserer ikke melding når person ikke finnes`() {
        runBlocking {
            every { personRepository.hentPerson(ident) } returns null

            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())

            rapidsConnection.inspektør.size shouldBe 0
        }
    }

    @Test
    fun `kaster exception hvis perioden ikke er avsluttet`() {
        runBlocking {
            val person = person(ansvarligSystem = AnsvarligSystem.DP)
            val aktivPeriode = avsluttetPeriode().copy(avsluttet = null)
            every { personRepository.hentPerson(ident) } returns person

            shouldThrow<IllegalArgumentException> {
                arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(aktivPeriode)
            }

            rapidsConnection.inspektør.size shouldBe 0
        }
    }

    @Test
    fun `kaster exception videre og publiserer ikke melding hvis meldekortregister feiler`() {
        runBlocking {
            val person = person(ansvarligSystem = AnsvarligSystem.DP)
            every { personRepository.hentPerson(ident) } returns person
            every { personRepository.hentÅrsakTilUtmelding(periodeId, ident) } returns null
            coEvery {
                meldekortregisterConnector.hentSisteFastsattMeldedato(ident)
            } throws RuntimeException("Meldekortregister utilgjengelig")

            shouldThrow<RuntimeException> {
                arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())
            }

            rapidsConnection.inspektør.size shouldBe 0
        }
    }

    private fun avsluttetPeriode() =
        Arbeidssøkerperiode(
            periodeId = periodeId,
            ident = ident,
            startet = startet,
            avsluttet = avregistrertTidspunkt,
            overtattBekreftelse = null,
        )

    private fun person(ansvarligSystem: AnsvarligSystem? = null) =
        Person(ident = ident).apply {
            setStatus(Status.DAGPENGERBRUKER)
            setAnsvarligSystem(ansvarligSystem)
        }
}
