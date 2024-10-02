package no.nav.dagpenger.rapportering.personregister.modell

interface StatusStrategy {
    fun håndter(
        person: Person,
        hendelse: Hendelse,
    )
}

class DefaultStatusStrategy : StatusStrategy {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        person.statusHistorikk.put(hendelse.dato, hendelse.status)
    }
}

class InnvilgetStatusStrategy : StatusStrategy {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        if (hendelse.status != Status.SØKT) {
            person.statusHistorikk.put(hendelse.dato, hendelse.status)
        }
    }
}

interface StatusStrategyFactory {
    fun createStrategy(person: Person): StatusStrategy
}

class SimpleStatusStrategyFactory : StatusStrategyFactory {
    override fun createStrategy(person: Person): StatusStrategy =
        when (person.status) {
            Status.INNVILGET -> InnvilgetStatusStrategy()
            else -> DefaultStatusStrategy()
        }
}
