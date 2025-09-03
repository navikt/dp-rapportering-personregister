package no.nav.dagpenger.rapportering.personregister.modell.meldestatus

import java.time.LocalDate

data class MeldestatusRequest(
    val arenaPersonId: Long? = null,
    val personident: String? = null,
    val sokeDato: LocalDate? = null,
)
