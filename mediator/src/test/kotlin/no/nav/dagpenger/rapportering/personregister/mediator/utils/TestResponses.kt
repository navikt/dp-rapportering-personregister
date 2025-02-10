package no.nav.dagpenger.rapportering.personregister.mediator.utils

import no.nav.dagpenger.rapportering.personregister.mediator.connector.ArbeidssøkerperiodeResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.BrukerResponse
import no.nav.dagpenger.rapportering.personregister.mediator.connector.MetadataResponse
import java.time.LocalDateTime
import java.util.UUID

fun arbeidssøkerResponse(
    periodeId: UUID,
    inkluderAvsluttet: Boolean = false,
) = ArbeidssøkerperiodeResponse(
    periodeId = periodeId,
    startet =
        MetadataResponse(
            tidspunkt = LocalDateTime.now().minusWeeks(3),
            utfoertAv =
                BrukerResponse(
                    type = "SLUTTBRUKER",
                    id = "12345678910",
                ),
            kilde = "kilde",
            aarsak = "aarsak",
            tidspunktFraKilde = null,
        ),
    avsluttet =
        if (inkluderAvsluttet) {
            MetadataResponse(
                tidspunkt = LocalDateTime.now().minusDays(2),
                utfoertAv =
                    BrukerResponse(
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
)
