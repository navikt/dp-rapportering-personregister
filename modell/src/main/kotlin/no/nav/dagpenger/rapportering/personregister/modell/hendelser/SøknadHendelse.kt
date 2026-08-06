package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

data class SøknadHendelse(
    override val korrelasjonsId: UUID?,
    override val ident: String,
    override val dato: LocalDateTime = now(),
    override val startDato: LocalDateTime,
    override val referanseId: String,
) : Hendelse {
    override val sluttDato = null
    override val kilde: Kildesystem = Kildesystem.Søknad
}
