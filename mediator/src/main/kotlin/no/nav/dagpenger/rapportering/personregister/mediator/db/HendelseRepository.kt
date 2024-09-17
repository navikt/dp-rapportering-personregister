package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse

interface HendelseRepository {
    fun finnHendelser(ident: String): List<Hendelse>

    fun opprettHendelse(hendelse: Hendelse)
}
