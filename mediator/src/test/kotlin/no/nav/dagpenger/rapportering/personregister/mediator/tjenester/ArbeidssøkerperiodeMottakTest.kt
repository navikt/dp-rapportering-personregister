package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.ArbeidssøkerHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.TestProdusent
import no.nav.dagpenger.rapportering.personregister.mediator.kafka.startLytting
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.Bruker
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.Periode
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy
import org.apache.kafka.clients.producer.MockProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.StringSerializer
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID
import no.nav.dagpenger.rapportering.personregister.modell.arbeidssøker.Metadata as PeriodeMetadata

class ArbeidssøkerperiodeMottakTest {
    private val topic = "paw.arbeidssokerperioder-v1"
    private val partition = TopicPartition(topic, 0)
    private val kafkaConsumer =
        MockConsumer<String, String>(OffsetResetStrategy.EARLIEST).apply {
            updateBeginningOffsets(mapOf(partition to 0L))
        }

    private val kafkaProducer = MockProducer(true, StringSerializer(), StringSerializer())
    private val testProdusent = TestProdusent(kafka = kafkaProducer, topic = topic)
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    init {
        val mottak = ArbeidssøkerperiodeMottak(kafkaConsumer, personstatusMediator)

        startLytting(mottak)
    }

    /*@BeforeEach
    fun setup() {
        testProdusent.reset()
    }*/

    @Test
    @Disabled
    fun `skal motta arbeidssøkerperiode event`() {
        testProdusent.publiser(verdi = periodeHendelse(UUID.randomUUID()))

        verify(exactly = 1) { personstatusMediator.behandle(any<ArbeidssøkerHendelse>()) }
    }
}

private fun periodeHendelse(
    periodeId: UUID,
    inkluderAvsluttet: Boolean = true,
) = defaultObjectMapper
    .writeValueAsString(
        listOf(
            Periode(
                id = periodeId,
                identitetsnummer = "12345678910",
                startet =
                    PeriodeMetadata(
                        tidspunkt = LocalDateTime.now().minusWeeks(3).toTimestampMillis(),
                        utfoertAv =
                            Bruker(
                                type = "SLUTTBRUKER",
                                id = "12345678910",
                            ),
                        kilde = "kilde",
                        aarsak = "aarsak",
                        tidspunktFraKilde = null,
                    ),
                avsluttet =
                    if (inkluderAvsluttet) {
                        PeriodeMetadata(
                            tidspunkt = LocalDateTime.now().minusDays(2).toTimestampMillis(),
                            utfoertAv =
                                Bruker(
                                    type = "SYSTEM",
                                    id = "paw-arbeidssoekerregisteret-bekreftelse-utgang:24.11.01.38-1",
                                ),
                            kilde = "kilde",
                            aarsak = "Graceperiode utløpt",
                            tidspunktFraKilde = null,
                        )
                    } else {
                        null
                    },
            ),
        ),
    )

private fun LocalDateTime.toTimestampMillis() = this.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
