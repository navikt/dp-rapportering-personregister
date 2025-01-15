import no.nav.dagpenger.rapportering.personregister.modell.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.Status.ARBS
import no.nav.dagpenger.rapportering.personregister.modell.Status.AVSLÅTT
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_ARBS
import no.nav.dagpenger.rapportering.personregister.modell.Status.INNVILGET
import no.nav.dagpenger.rapportering.personregister.modell.Status.STANSET
import no.nav.dagpenger.rapportering.personregister.modell.Status.SØKT

interface StatusStrategy {
    fun håndter(
        person: Person,
        hendelse: Hendelse,
    )
}

abstract class BaseStatusStrategy : StatusStrategy {
    protected fun oppdaterStatus(
        person: Person,
        hendelse: Hendelse,
        tillatteStatus: Set<Status>,
    ) {
        val nyStatus = hendelse.status
        if (person.status != nyStatus && nyStatus in tillatteStatus) {
            person.statusHistorikk.put(hendelse.dato, nyStatus)
        }
    }
}

object DefaultStatusStrategy : StatusStrategy {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        person.statusHistorikk.put(hendelse.dato, hendelse.status)
    }
}

object SøktStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.ARBS, Status.IKKE_ARBS, Status.INNVILGET, Status.AVSLÅTT))
    }
}

object ArbsStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.SØKT, Status.INNVILGET, Status.AVSLÅTT, Status.IKKE_ARBS))
    }
}

object IkkeArbsStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.ARBS, Status.SØKT, Status.AVSLÅTT))
    }
}

object AvslåttStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.SØKT, Status.INNVILGET))
    }
}

object InnvilgetStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.STANSET))
    }
}

object StansetStatusStrategy : BaseStatusStrategy() {
    override fun håndter(
        person: Person,
        hendelse: Hendelse,
    ) {
        oppdaterStatus(person, hendelse, setOf(Status.SØKT, Status.INNVILGET))
    }
}

interface StatusStrategyFactory {
    fun createStrategy(person: Person): StatusStrategy
}

class SimpleStatusStrategyFactory : StatusStrategyFactory {
    override fun createStrategy(person: Person): StatusStrategy =

        try {
            when (person.status) {
                SØKT -> SøktStatusStrategy
                ARBS -> ArbsStatusStrategy
                AVSLÅTT -> AvslåttStatusStrategy
                INNVILGET -> InnvilgetStatusStrategy
                STANSET -> StansetStatusStrategy
                IKKE_ARBS -> IkkeArbsStatusStrategy
            }
        } catch (e: Exception) {
            DefaultStatusStrategy
        }
}
