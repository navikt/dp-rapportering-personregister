package no.nav.dagpenger.rapportering.personregister.mediator.connector

import io.kotest.assertions.throwables.shouldThrow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import no.nav.dagpenger.rapportering.personregister.kafka.utils.sendDeferred
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelse
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bekreftelsesløsning
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Bruker
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SendtInnAv
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.Svar
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse as ASRBekreftelse

class ArbeidssøkerBekreftelseConnectorTest {
    private val producer = mockk<Producer<Long, ASRBekreftelse>>()
    private val connector = ArbeidssøkerBekreftelseKafka(producer)

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
    }

    @Suppress("DeferredResultUnused")
    @Test
    fun `sender bekreftelse og returnerer bekreftelseId`() {
        val metadata = RecordMetadata(TopicPartition("topic", 0), 0, 0, 0, 0, 0)
        val melding = bekreftelseMelding()
        every { producer.sendDeferred(any<ProducerRecord<Long, ASRBekreftelse>>()) } returns CompletableDeferred(metadata)

        runBlocking { connector.sendBekreftelse(1L, melding) }

        verify(exactly = 1) { producer.sendDeferred(any()) }
    }

    @Test
    fun `kaster exception når sending feiler`() {
        val deferred = CompletableDeferred<RecordMetadata>()
        deferred.completeExceptionally(RuntimeException("Kafka feil"))

        every { producer.sendDeferred(any<ProducerRecord<Long, ASRBekreftelse>>()) } returns deferred

        val melding = bekreftelseMelding()

        shouldThrow<RuntimeException> {
            runBlocking { connector.sendBekreftelse(1L, melding) }
        }
    }

    private fun bekreftelseMelding(
        bekreftelseId: UUID = UUID.randomUUID(),
        periodeId: UUID = UUID.randomUUID(),
    ) = ArbeidssøkerBekreftelseMelding(
        ident = "12345678910",
        bekreftelse =
            Bekreftelse(
                id = bekreftelseId,
                periodeId = periodeId,
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
                        gjelderFra = LocalDateTime.of(2025, 1, 1, 0, 0, 0),
                        gjelderTil = LocalDateTime.of(2025, 1, 14, 23, 59, 59),
                        harJobbetIDennePerioden = true,
                        vilFortsetteSomArbeidssøker = true,
                    ),
            ),
    )
}
