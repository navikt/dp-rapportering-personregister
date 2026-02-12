package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.arbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7.newUuid
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.paw.arbeidssokerregisteret.api.v1.Metadata
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import java.time.Instant.now

class ArbeidssøkerMottakTest {
    val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    val arbeidssøkerMottak =
        ArbeidssøkerMottak(arbeidssøkerMediator = arbeidssøkerMediator, arbeidssøkerperiodeMetrikker)

    @Test
    fun `consume behandler melding og inkrementerer metrikk`() {
        val metrikkCount = arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.count()

        arbeidssøkerMottak.consume(lagConsumerRecords())

        verify { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) }
        arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `consume kaster exception og inkrementerer metrikk hvis behandling av melding feiler`() {
        val metrikkCount = arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeFeilet.count()
        every { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) } throws RuntimeException("kaboom")

        val exception = shouldThrow<RuntimeException> { arbeidssøkerMottak.consume(lagConsumerRecords()) }

        exception.message shouldBe "kaboom"
        arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeFeilet.count() shouldBe metrikkCount + 1
    }

    private fun lagConsumerRecords(): ConsumerRecords<Long, Periode> =
        ConsumerRecords(
            mapOf<TopicPartition, List<ConsumerRecord<Long, Periode>>>(
                Pair(
                    TopicPartition("", 0),
                    listOf(
                        ConsumerRecord<Long, Periode>(
                            "topic",
                            0,
                            0,
                            0,
                            Periode(
                                newUuid(),
                                "13308825099",
                                Metadata(now(), null, null, null, null),
                                Metadata(now(), null, null, null, null),
                            ),
                        ),
                    ),
                ),
            ),
            mapOf<TopicPartition, OffsetAndMetadata>(),
        )
}
