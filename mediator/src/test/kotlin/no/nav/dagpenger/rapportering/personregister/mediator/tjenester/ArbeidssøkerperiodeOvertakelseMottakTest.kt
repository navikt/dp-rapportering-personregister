package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.ArbeidssøkerMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7.newUuid
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.paavegneav.v1.vo.Start
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import tools.jackson.databind.ObjectMapper
import java.util.UUID

class ArbeidssøkerperiodeOvertakelseMottakTest {
    private val ident = "13308825099"

    private val arbeidssøkerMediator = mockk<ArbeidssøkerMediator>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)
    private val mottakk = ArbeidssøkerperiodeOvertakelseMottak(arbeidssøkerMediator, meldingerRepository)

    @Test
    fun `consume behandler melding og lagrer innkommende melding`() {
        val periodeId = newUuid()
        val records = lagConsumerRecords(periodeId)
        mottakk.consume(records)

        coVerify(exactly = 1) { arbeidssøkerMediator.behandle(any<PaaVegneAv>(), 1, true) }
        verify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                any(),
                null,
                match { melding ->
                    with(ObjectMapper().readTree(melding)) {
                        this["@event_name"].asString() == "mottok_arbeidssøkerperiode_overtakelse" &&
                            with(this["paVegneAv"]) {
                                this["periodeId"].asString() == periodeId.toString() &&
                                    this["bekreftelsesloesning"].asString() == Bekreftelsesloesning.DAGPENGER.toString() &&
                                    this["handling"].asString() == "Start"
                            }
                    }
                },
            )
        }
    }

    private fun lagConsumerRecords(periodeId: UUID): ConsumerRecords<Long, PaaVegneAv> =
        ConsumerRecords(
            mapOf<TopicPartition, List<ConsumerRecord<Long, PaaVegneAv>>>(
                Pair(
                    TopicPartition("", 0),
                    listOf(
                        ConsumerRecord<Long, PaaVegneAv>(
                            "topic",
                            0,
                            0,
                            0,
                            PaaVegneAv(periodeId, Bekreftelsesloesning.DAGPENGER, Start()),
                        ),
                    ),
                ),
            ),
            mapOf<TopicPartition, OffsetAndMetadata>(),
        )
}
