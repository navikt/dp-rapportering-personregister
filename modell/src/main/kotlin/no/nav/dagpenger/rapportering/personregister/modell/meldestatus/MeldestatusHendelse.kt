package no.nav.dagpenger.rapportering.personregister.modell.meldestatus

data class MeldestatusHendelse(
    val korrelasjonsId: String?,
    val personId: Long,
    val meldestatusId: Long,
    val hendelseId: Long,
)
