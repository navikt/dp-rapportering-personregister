package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import java.util.UUID

interface MeldingerRepository {
    fun lagreInnkommendeMelding(
        korrelasjonsId: UUID = UUIDv7.newUuid(),
        ident: String? = null,
        relevantMeldingsinnhold: String,
    )

    fun lagreUtgåendeMelding(
        korrelasjonsId: UUID = UUIDv7.newUuid(),
        ident: String,
        melding: String,
    )
}
