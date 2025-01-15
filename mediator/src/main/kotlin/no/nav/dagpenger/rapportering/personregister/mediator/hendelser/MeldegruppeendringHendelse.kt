package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

import java.time.LocalDate

data class MeldegruppeendringHendelse(
    val ident: String,
    val meldegruppeKode: String,
    val fraOgMed: LocalDate,
    val tilOgMed: LocalDate? = null,
    val hendelseId: String,
)
