package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

data class VedtakFattetUtenforArenaHendelse(
    override val korrelasjonsId: UUID?,
    override val ident: String,
    override val dato: LocalDateTime = now(),
    override val referanseId: String,
    val behandlingId: String,
    val søknadId: String,
    val sakId: String,
) : Hendelse {
    override val startDato: LocalDateTime = dato
    override val sluttDato: LocalDateTime? = null
    override val kilde: Kildesystem = Kildesystem.DpSaksbehandling

    override fun behandle(person: Person) {}
}
