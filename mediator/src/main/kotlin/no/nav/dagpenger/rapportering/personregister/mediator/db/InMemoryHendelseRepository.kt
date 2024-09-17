package no.nav.dagpenger.rapportering.personregister.mediator.db

import no.nav.dagpenger.rapportering.personregister.modell.Hendelse

class InMemoryHendelseRepository : HendelseRepository {
    private val hendelseList = mutableMapOf<String, Hendelse>()

    override fun finnHendelser(ident: String): List<Hendelse> = hendelseList.filter { it.key == ident }.map { it.value }

    override fun opprettHendelse(hendelse: Hendelse) {
        hendelseList[hendelse.ident] = hendelse
    }
}
