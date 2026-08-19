package no.nav.dagpenger.rapportering.personregister.modell.meldestatus

import java.util.UUID

data class MeldestatusHendelse(
    val korrelasjonsId: UUID,
    val personId: Long,
    val meldestatusId: Long,
    val hendelseId: Long,
)
