package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import io.getunleash.FakeUnleash
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
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
import tools.jackson.databind.ObjectMapper
import java.time.Instant.now
import java.time.LocalDateTime

class ArbeidssøkerMottakTest {
    private val ident = "13308825099"

    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    private val arbeidssøkerService = mockk<ArbeidssøkerService>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)
    private val fakeUnleash = FakeUnleash()
    private val arbeidssøkerMottak =
        ArbeidssøkerMottak(
            arbeidssøkerMediator,
            arbeidssøkerperiodeMetrikker,
            arbeidssøkerService,
            fakeUnleash,
            meldingerRepository,
        )

    @Test
    fun `consume behandler melding og inkrementerer metrikk`() {
        val metrikkCount = arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeMottatt.count()

        val records = lagConsumerRecords()
        val periode = records.first().value()
        arbeidssøkerMottak.consume(records)

        verify(exactly = 1) { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "mottok_arbeidssøkerperiode" &&
                            with(this["arbeidssøkerperiode"]) {
                                this["ident"].asString() == periode.identitetsnummer &&
                                    this["periodeId"].asString() == periode.id.toString() &&
                                    this["startet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.startet.tidspunkt, ZONE_ID) &&
                                    this["avsluttet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.avsluttet?.tidspunkt, ZONE_ID) &&
                                    this["overtattBekreftelse"].isNull
                            }
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

        val records = lagConsumerRecords()
        val periode = records.first().value()
        val exception = shouldThrow<RuntimeException> { arbeidssøkerMottak.consume(records) }

        exception.message shouldBe "kaboom"
        verify(exactly = 1) { arbeidssøkerMediator.behandle(any<Arbeidssøkerperiode>()) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "mottok_arbeidssøkerperiode" &&
                            with(this["arbeidssøkerperiode"]) {
                                this["ident"].asString() == periode.identitetsnummer &&
                                    this["periodeId"].asString() == periode.id.toString() &&
                                    this["startet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.startet.tidspunkt, ZONE_ID) &&
                                    this["avsluttet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.avsluttet?.tidspunkt, ZONE_ID) &&
                                    this["overtattBekreftelse"].isNull
                            }
                    }
                },
            )
        }
        arbeidssøkerperiodeMetrikker.arbeidssøkerperiodeFeilet.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `publiserAvsluttetArbeidssøkerperiode kalles når toggle er på og periode er avsluttet`() {
        fakeUnleash.enable("dp-rapportering-personregister-publiser-avsluttet-arbeidssokerperiode")

        val records = lagConsumerRecords(avsluttet = true)
        val periode = records.first().value()
        arbeidssøkerMottak.consume(records)

        coVerify(exactly = 1) { arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(any(), any()) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "mottok_arbeidssøkerperiode" &&
                            with(this["arbeidssøkerperiode"]) {
                                this["ident"].asString() == periode.identitetsnummer &&
                                    this["periodeId"].asString() == periode.id.toString() &&
                                    this["startet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.startet.tidspunkt, ZONE_ID) &&
                                    this["avsluttet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.avsluttet?.tidspunkt, ZONE_ID) &&
                                    this["overtattBekreftelse"].isNull
                            }
                    }
                },
            )
        }
    }

    @Test
    fun `publiserAvsluttetArbeidssøkerperiode kalles ikke når toggle er av og periode er avsluttet`() {
        fakeUnleash.disable("dp-rapportering-personregister-publiser-avsluttet-arbeidssokerperiode")

        val records = lagConsumerRecords(avsluttet = true)
        val periode = records.first().value()
        arbeidssøkerMottak.consume(records)

        coVerify(exactly = 0) { arbeidssøkerService.publiserAvsluttetArbeidssøkerperiode(any(), any()) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                ident,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "mottok_arbeidssøkerperiode" &&
                            with(this["arbeidssøkerperiode"]) {
                                this["ident"].asString() == periode.identitetsnummer &&
                                    this["periodeId"].asString() == periode.id.toString() &&
                                    this["startet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.startet.tidspunkt, ZONE_ID) &&
                                    this["avsluttet"].asLocalDateTime() == LocalDateTime.ofInstant(periode.avsluttet?.tidspunkt, ZONE_ID) &&
                                    this["overtattBekreftelse"].isNull
                            }
                    }
                },
            )
        }
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
                                ident,
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
