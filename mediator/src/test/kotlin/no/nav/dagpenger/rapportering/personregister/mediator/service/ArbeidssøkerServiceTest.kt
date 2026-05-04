package no.nav.dagpenger.rapportering.personregister.mediator.service

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder
import no.nav.dagpenger.rapportering.personregister.mediator.ApplicationBuilder.Companion.getRapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.InnsendtMeldekortResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MeldekortregisterConnector
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.mediator.utils.arbeidssøkerResponse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import org.apache.kafka.clients.producer.Producer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ArbeidssøkerServiceTest {
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>(relaxed = true)
    private val meldekortregisterConnector = mockk<MeldekortregisterConnector>(relaxed = true)
    private val bekreftelseKafkaProdusent = mockk<Producer<Long, Bekreftelse>>(relaxed = true)

    private val arbeidssøkerService = ArbeidssøkerService(arbeidssøkerConnector, meldekortregisterConnector)

    private val ident = "12345678901"
    private val periodeId = UUIDv7.newUuid()
    private val startet = LocalDateTime.now().minusWeeks(3)
    private val avsluttetTidspunkt = LocalDateTime.now().minusDays(1)

    private lateinit var testRapid: TestRapid

    @BeforeEach
    fun setup() {
        testRapid = TestRapid()
        mockkObject(ApplicationBuilder.Companion)
        every { getRapidsConnection() } returns testRapid
    }

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
    fun `publiserer avsluttet_arbeidssokerperiode med fastsattMeldingsdag når innsendt meldekort finnes`() {
        val tilOgMed = LocalDateTime.now().minusDays(2)
        val meldekort =
            InnsendtMeldekortResponse(
                fraOgMed = LocalDateTime.now().minusWeeks(2),
                tilOgMed = tilOgMed,
                innsendtTidspunkt = LocalDateTime.now().minusDays(3),
            )
        coEvery { meldekortregisterConnector.hentSisteInnsendteMeldekort() } returns meldekort

        val periode = avsluttetPeriode()
        arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(periode)

        testRapid.inspektør.size shouldBe 1
        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "avsluttet_arbeidssokerperiode"
        message["ident"].asText() shouldBe ident
        message["avsluttetTidspunkt"].asLocalDateTime() shouldBe avsluttetTidspunkt
        message["fastsattMeldingsdag"].asLocalDateTime() shouldBe tilOgMed.plusDays(1)
    }

    @Test
    fun `publiserer avsluttet_arbeidssokerperiode uten fastsattMeldingsdag når ingen innsendte meldekort finnes`() {
        coEvery { meldekortregisterConnector.hentSisteInnsendteMeldekort() } returns null

        val periode = avsluttetPeriode()
        arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(periode)

        testRapid.inspektør.size shouldBe 1
        val message = testRapid.inspektør.message(0)
        message["@event_name"].asText() shouldBe "avsluttet_arbeidssokerperiode"
        message["ident"].asText() shouldBe ident
        message["avsluttetTidspunkt"].asLocalDateTime() shouldBe avsluttetTidspunkt
        message["fastsattMeldingsdag"] shouldBe null
    }

    @Test
    fun `kaster exception hvis perioden ikke er avsluttet`() {
        val aktivPeriode =
            Arbeidssøkerperiode(
                periodeId = periodeId,
                ident = ident,
                startet = startet,
                avsluttet = null,
                overtattBekreftelse = null,
            )

        shouldThrow<IllegalArgumentException> {
            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(aktivPeriode)
        }

        testRapid.inspektør.size shouldBe 0
    }

    @Test
    fun `kaster exception videre og publiserer ikke melding hvis meldekortregister feiler`() {
        coEvery {
            meldekortregisterConnector.hentSisteInnsendteMeldekort()
        } throws RuntimeException("Meldekortregister utilgjengelig")

        shouldThrow<RuntimeException> {
            arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(avsluttetPeriode())
        }

        testRapid.inspektør.size shouldBe 0
    }

    private fun avsluttetPeriode() =
        Arbeidssøkerperiode(
            periodeId = periodeId,
            ident = ident,
            startet = startet,
            avsluttet = avsluttetTidspunkt,
            overtattBekreftelse = null,
        )
}
