package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem.Arbeidssokerregisteret
import no.nav.dagpenger.rapportering.personregister.modell.Status.ARBS
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
        // Trenger vi en ny status her hvis bruker ikke lenger er arbeidssøker?
        status = if (sluttDato == null) ARBS else ARBS,
        kilde = Arbeidssokerregisteret,
    )
