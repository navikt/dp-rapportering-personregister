package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import java.util.UUID

interface MeldingerRepository {
    fun lagreInnkommendeMelding(
        korrelasjonsId: UUID = UUIDv7.newUuid(),
        ident: String? = null,
        relevantMeldingsinnhold: String,
    ): Int

    fun lagreUtgåendeMelding(
        korrelasjonsId: UUID,
        ident: String,
        melding: String,
    ): Int
}
