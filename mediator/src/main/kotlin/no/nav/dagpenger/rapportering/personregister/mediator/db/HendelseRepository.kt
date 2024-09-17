package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse

interface HendelseRepository {
    fun finnHendelser(hendelseId: String): List<Hendelse>

    fun opprettHendelse(hendelse: Hendelse)
}
