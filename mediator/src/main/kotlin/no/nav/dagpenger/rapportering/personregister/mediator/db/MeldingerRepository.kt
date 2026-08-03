package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7

interface MeldingerRepository {
    fun lagreInnkommendeMelding(
        korrelasjonsId: String = UUIDv7.newUuid().toString(),
        ident: String? = null,
        relevantMeldingsinnhold: String,
    )

    fun lagreUtgåendeMelding(
        korrelasjonsId: String,
        ident: String,
        melding: String,
    )
}
