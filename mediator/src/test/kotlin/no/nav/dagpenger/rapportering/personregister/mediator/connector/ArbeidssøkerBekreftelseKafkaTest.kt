package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.kafka.MockKafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.BeforeEach
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.Test
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse as ASRBekreftelse

class ArbeidssøkerBekreftelseKafkaTest {
    private val meldingerRepository = mockk<MeldingerRepository>()
    private lateinit var producer: MockKafkaProducer<ASRBekreftelse>
    private lateinit var arbeidssøkerBekreftelseKafka: ArbeidssøkerBekreftelseKafka

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
        System.setProperty("BEKREFTELSE_TOPIC", "arbeidssøkerbekreftelse")
    }

    @BeforeEach
    fun setup() {
        mockkStatic("no.nav.dagpenger.rapportering.personregister.kafka.utils.ProducerUtilsKt")

        producer = MockKafkaProducer()
        arbeidssøkerBekreftelseKafka =
            ArbeidssøkerBekreftelseKafka(
                bekreftelseKafkaProdusent = producer,
                arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker = arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker,
                meldingerRepository = meldingerRepository,
            )
    }

    @Test
    fun `sender bekreftelse og inkrementerer metrikk`() {
        val korrelasjonsId = UUID.randomUUID()
        val metrikk = arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker.arbeidssøkerbekreftelseUtsendt.count()

        every { meldingerRepository.lagreUtgåendeMelding(any(), any(), any()) } returns 1

        runBlocking {
            arbeidssøkerBekreftelseKafka.sendBekreftelse(123456789, bekreftelseMelding(), korrelasjonsId)

            coVerify(exactly = 1) { producer.sendDeferred(any()) }
            verify(exactly = 1) {
                meldingerRepository.lagreUtgåendeMelding(
                    korrelasjonsId,
                    bekreftelseMelding().ident,
                    producer.meldinger
                        .first()
                        .value()
                        .toString(),
                )
            }

            arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker.arbeidssøkerbekreftelseUtsendt.count() shouldBe metrikk + 1
        }
    }

    @Test
    fun `kaster exception når sending av bekreftelse feiler og inkrementerer metrikk`() {
        val metrikk =
            arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker.arbeidssøkerbekreftelseUtsendingFeilet.count()

        val deferred = CompletableDeferred<RecordMetadata>()
        deferred.completeExceptionally(RuntimeException("Kafka feil"))

        every { producer.sendDeferred(any<ProducerRecord<Long, ASRBekreftelse>>()) } returns deferred

        shouldThrow<RuntimeException> {
            runBlocking { arbeidssøkerBekreftelseKafka.sendBekreftelse(1L, bekreftelseMelding(), UUID.randomUUID()) }
        }

        verify(exactly = 0) { meldingerRepository.lagreUtgåendeMelding(any(), any(), any()) }

        arbeidssøkerBekreftelseTilArbeidssøkerregisteretMetrikker.arbeidssøkerbekreftelseUtsendingFeilet.count() shouldBe metrikk + 1
    }

    private fun bekreftelseMelding() =
        ArbeidssøkerBekreftelseMelding(
            ident = "12345678910",
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
                                            ident = "12345678910",
                                            sikkerhetsnivå = "idporten:Level4",
                                        ),
                                    kilde = "DAGPENGER",
                                    årsak = "Bruker sendte inn dagpengermeldekort",
                                ),
                            gjelderFra = LocalDateTime.of(2025, 1, 1, 0, 0),
                            gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59),
                            harJobbetIDennePerioden = true,
                            vilFortsetteSomArbeidssøker = true,
                        ),
                ),
        )
}
