package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import java.time.LocalDateTime
import java.time.LocalDateTime.now

fun lagSøknadHendelse(
    ident: String,
    referanseId: String = "123",
    startDato: LocalDateTime = now(),
) = SøknadHendelse(
    ident = ident,
    referanseId = referanseId,
    dato = now(),
    startDato = startDato,
)
