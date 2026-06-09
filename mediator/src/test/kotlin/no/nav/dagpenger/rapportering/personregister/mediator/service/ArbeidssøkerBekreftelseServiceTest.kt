package no.nav.dagpenger.rapportering.personregister.mediator.service

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerBekreftelseKafka
import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerConnector
import no.nav.dagpenger.rapportering.personregister.mediator.connector.RecordKeyResponse
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode.ÅrsakTilUtmelding.UTMELDT_I_ARBEIDSSØKERREGISTERET
import no.nav.dagpenger.rapportering.personregister.modell.Person
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class ArbeidssøkerBekreftelseServiceTest {
    private val arbeidssøkerConnector = mockk<ArbeidssøkerConnector>()
    private val arbeidssøkerBekreftelseConnector = mockk<ArbeidssøkerBekreftelseKafka>()
    private val personRepositoryMock = mockk<PersonRepository>()

    private val service =
        ArbeidssøkerBekreftelseService(
            arbeidssøkerConnector,
            arbeidssøkerBekreftelseConnector,
            personRepositoryMock,
        )

    private val ident = "12345678910"
    private val recordKey = 42L

    @Test
    fun `lagrer årsak og sender bekreftelse dersom vilFortsetteSomArbeidssøker er false og dp har ansvar for arbeidssøkerbekreftelse`() =
        runBlocking {
            val melding = bekreftelseMelding(vilFortsetteSomArbeidssøker = false)

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns person(dpHarAnsvarForArbeidssøkerbekreftelse = true)
            coEvery { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) } returns melding.bekreftelse.id
            coEvery { personRepositoryMock.lagreÅrsakTilUtmelding(any(), any(), any()) } returns Unit

            service.behandle(melding)

            coVerify(exactly = 1) { personRepositoryMock.hentPerson(ident) }
            coVerify(exactly = 1) {
                personRepositoryMock.lagreÅrsakTilUtmelding(
                    melding.bekreftelse.periodeId,
                    ident = ident,
                    Arbeidssøkerperiode.ÅrsakTilUtmelding.UTMELDT_PÅ_MELDEKORT,
                )
            }
            coVerify(exactly = 1) { arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding) }
        }

    @Test
    fun `sender ikke bekreftelse og lagrer ikke årsak dersom dp ikke har ansvar for arbeidssøkerbekreftelse`() =
        runBlocking {
            val melding = bekreftelseMelding(vilFortsetteSomArbeidssøker = false)

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns person(dpHarAnsvarForArbeidssøkerbekreftelse = false)

            service.behandle(melding)

            coVerify(exactly = 1) { personRepositoryMock.hentPerson(ident) }
            coVerify(exactly = 0) { personRepositoryMock.lagreÅrsakTilUtmelding(any(), any(), any()) }
            coVerify(exactly = 0) { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) }
        }

    @Test
    fun `sender bekreftelse uten å lagre årsak dersom vilFortsetteSomArbeidssøker er true og dp har ansvar`() =
        runBlocking {
            val melding = bekreftelseMelding(vilFortsetteSomArbeidssøker = true)

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns person(dpHarAnsvarForArbeidssøkerbekreftelse = true)
            coEvery { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) } returns melding.bekreftelse.id

            service.behandle(melding)

            coVerify(exactly = 1) { personRepositoryMock.hentPerson(ident) }
            coVerify(exactly = 0) { personRepositoryMock.lagreÅrsakTilUtmelding(any(), any(), any()) }
            coVerify(exactly = 1) { arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding) }
        }

    @Test
    fun `henter recordKey og sender bekreftelse`() =
        runBlocking {
            val melding = bekreftelseMelding()

            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns person(dpHarAnsvarForArbeidssøkerbekreftelse = true)
            coEvery { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) } returns melding.bekreftelse.id

            service.behandle(melding)

            coVerify(exactly = 1) { arbeidssøkerConnector.hentRecordKey(ident) }
            coVerify(exactly = 1) { personRepositoryMock.hentPerson(ident) }
            coVerify(exactly = 1) { arbeidssøkerBekreftelseConnector.sendBekreftelse(recordKey, melding) }
        }

    @Test
    fun `kaster exception når hentRecordKey feiler`() =
        runBlocking {
            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } throws RuntimeException("Feil")

            shouldThrow<RuntimeException> { service.behandle(bekreftelseMelding()) }

            coVerify(exactly = 0) { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) }
        }

    @Test
    fun `kaster exception når person ikke finnes`() =
        runBlocking {
            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns null

            shouldThrow<IllegalStateException> { service.behandle(bekreftelseMelding()) }

            coVerify(exactly = 1) { personRepositoryMock.hentPerson(ident) }
            coVerify(exactly = 0) { arbeidssøkerBekreftelseConnector.sendBekreftelse(any(), any()) }
        }

    @Test
    fun `kaster exception når sendBekreftelse feiler`(): Unit =
        runBlocking {
            coEvery { arbeidssøkerConnector.hentRecordKey(ident) } returns RecordKeyResponse(recordKey)
            coEvery { personRepositoryMock.hentPerson(ident) } returns person(dpHarAnsvarForArbeidssøkerbekreftelse = true)
            coEvery {
                arbeidssøkerBekreftelseConnector.sendBekreftelse(
                    any(),
                    any(),
                )
            } throws RuntimeException("Kafka feil")

            shouldThrow<RuntimeException> { service.behandle(bekreftelseMelding()) }
        }

    private fun person(dpHarAnsvarForArbeidssøkerbekreftelse: Boolean): Person =
        Person(
            ident,
            arbeidssøkerperioder =
                mutableListOf(
                    Arbeidssøkerperiode(
                        periodeId = UUID.randomUUID(),
                        ident = ident,
                        startet = LocalDateTime.now().minusDays(2),
                        avsluttet = null,
                        overtattBekreftelse = dpHarAnsvarForArbeidssøkerbekreftelse,
                        årsakTilUtmelding = UTMELDT_I_ARBEIDSSØKERREGISTERET,
                    ),
                ),
        )

    private fun bekreftelseMelding(vilFortsetteSomArbeidssøker: Boolean = true) =
        ArbeidssøkerBekreftelseMelding(
            ident = ident,
            bekreftelse =
                Bekreftelse(
                    id = UUID.randomUUID(),
                    periodeId = UUID.randomUUID(),
                    bekreftelsesløsning = Bekreftelsesløsning.DAGPENGER,
                    svar =
                        Svar(
                            sendtInnAv =
                                SendtInnAv(
                                    tidspunkt = LocalDateTime.now(),
                                    utførtAv =
                                        Bruker(
                                            type = "SLUTTBRUKER",
                                            ident = ident,
                                            sikkerhetsnivå = "idporten:Level4",
                                        ),
                                    kilde = "DAGPENGER",
                                    årsak = "Bruker sendte inn dagpengermeldekort",
                                ),
                            gjelderFra = LocalDateTime.of(2025, 1, 1, 0, 0),
                            gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59),
                            harJobbetIDennePerioden = true,
                            vilFortsetteSomArbeidssøker = vilFortsetteSomArbeidssøker,
                        ),
                ),
        )
}
