package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Status
import java.time.LocalDateTime
import java.util.UUID

data class SøknadHendelse(
    val ident: String,
    val referanseId: String,
    val dato: LocalDateTime,
)

fun SøknadHendelse.tilHendelse(): Hendelse {
    val ident = this.ident
    val referanseId = this.referanseId
    val dato = this.dato
    return Hendelse(UUID.randomUUID(), ident, referanseId, dato, Status.Søkt, Kildesystem.Søknad)
}
