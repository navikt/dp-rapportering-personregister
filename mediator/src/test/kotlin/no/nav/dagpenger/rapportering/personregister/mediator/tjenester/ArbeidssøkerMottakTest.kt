package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.fasterxml.jackson.module.kotlin.readValue
import io.getunleash.FakeUnleash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.service.ArbeidssøkerService
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
import java.time.LocalDateTime

class ArbeidssøkerMottakTest {
    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    private val arbeidssøkerService = mockk<ArbeidssøkerService>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)
    private val fakeUnleash = FakeUnleash()
    private val arbeidssøkerMottak =
        ArbeidssøkerMottak(
            arbeidssøkerMediator = arbeidssøkerMediator,
            arbeidssøkerperiodeMetrikker,
            arbeidssøkerService = arbeidssøkerService,
            unleash = fakeUnleash,
            meldingerRepository = meldingerRepository,
        )

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")
    }

    @Test
    fun `consume behandler melding, lagrer den og inkrementerer metrikk`() {
        val metrikkCount = arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.count()

        val records = lagConsumerRecords()
        arbeidssøkerMottak.consume(records)

        verify { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) }
        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = records.first().value().identitetsnummer,
                relevantMeldingsinnhold =
                    match { melding ->
                        with(defaultObjectMapper.readValue<Arbeidssøkerperiode>(melding)) {
                            this.ident == records.first().value().identitetsnummer &&
                                this.periodeId == records.first().value().id &&
                                this.startet ==
                                LocalDateTime.ofInstant(
                                    records
                                        .first()
                                        .value()
                                        .startet.tidspunkt,
                                    ZONE_ID,
                                ) &&
                                this.avsluttet ==
                                records.first().value().avsluttet?.let {
                                    LocalDateTime.ofInstant(
                                        it.tidspunkt,
                                        ZONE_ID,
                                    )
                                } &&
                                this.overtattBekreftelse == null
                        }
                    },
            )
        }
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

    @Test
    fun `publiserAvsluttetArbeidssøkerperiode kalles når toggle er på og periode er avsluttet`() {
        fakeUnleash.enable("dp-rapportering-personregister-publiser-avsluttet-arbeidssokerperiode")

        arbeidssøkerMottak.consume(lagConsumerRecords(avsluttet = true))

        coVerify(exactly = 1) { arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(any()) }
    }

    @Test
    fun `publiserAvsluttetArbeidssøkerperiode kalles ikke når toggle er av og periode er avsluttet`() {
        fakeUnleash.disable("dp-rapportering-personregister-publiser-avsluttet-arbeidssokerperiode")

        arbeidssøkerMottak.consume(lagConsumerRecords(avsluttet = true))

        coVerify(exactly = 0) { arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(any()) }
    }

    private fun lagConsumerRecords(avsluttet: Boolean = true): ConsumerRecords<Long, Periode> =
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
                                if (avsluttet) Metadata(now(), null, null, null, null) else null,
                            ),
                        ),
                    ),
                ),
            ),
            mapOf<TopicPartition, OffsetAndMetadata>(),
        )
}
