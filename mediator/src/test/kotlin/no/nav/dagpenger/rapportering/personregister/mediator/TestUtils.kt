package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime
import java.time.LocalDateTime.now

fun lagSøknadHendelse(
    ident: String,
    referanseId: String = "123",
    startDato: LocalDateTime = now(),
) = SøknadHendelse(
    korrelasjonsId = UUIDv7.newUuid().toString(),
    ident = ident,
    referanseId = referanseId,
    dato = now(),
    startDato = startDato,
)
