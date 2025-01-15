package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Arbeidssokerregisteret
import no.nav.dagpenger.rapportering.personregister.modell.Status.ARBS
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_ARBS
import java.time.LocalDateTime
import java.util.UUID

data class ArbeidssøkerHendelse(
    val ident: String,
    val periodeId: UUID,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime? = null,
)

fun ArbeidssøkerHendelse.tilHendelse(): Hendelse =
    Hendelse(
        ident = ident,
        referanseId = periodeId.toString(),
        dato = startDato,
        status = if (sluttDato == null) ARBS else IKKE_ARBS,
        kilde = Arbeidssokerregisteret,
    )
