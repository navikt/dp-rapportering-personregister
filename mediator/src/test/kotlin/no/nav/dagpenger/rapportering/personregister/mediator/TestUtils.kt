package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

fun lagSøknadHendelse(
    ident: String,
    referanseId: String = "123",
    startDato: LocalDateTime = now(),
    korrelasjonsId: UUID? = null,
) = SøknadHendelse(
    korrelasjonsId = korrelasjonsId,
    ident = ident,
    referanseId = referanseId,
    dato = now(),
    startDato = startDato,
)
