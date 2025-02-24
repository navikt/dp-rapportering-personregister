package no.nav.dagpenger.rapportering.personregister.modell
import no.nav.dagpenger.rapportering.personregister.modell.Status.Type

sealed interface Status {
    val type: Type

    enum class Type {
        DAGPENGERBRUKER,
        IKKE_DAGPENGERBRUKER,
    }

    fun håndter(
        hendelse: Hendelse,
        person: Person,
        action: (Status) -> Unit,
    ): Status

    companion object {
        fun rehydrer(type: String): Status =
            when (Type.valueOf(type)) {
                Type.DAGPENGERBRUKER -> Dagpengerbruker
                Type.IKKE_DAGPENGERBRUKER -> IkkeDagpengerbruker
            }

        internal fun oppdater(
            person: Person,
            nåværendeStatus: Status,
            action: (Status) -> Unit,
        ): Status {
            val nyStatus = if (person.oppfyllerKrav) Dagpengerbruker else IkkeDagpengerbruker

            return if (nyStatus != nåværendeStatus) nyStatus.also(action) else nåværendeStatus
        }
    }
}

data object Dagpengerbruker : Status {
    override val type = Type.DAGPENGERBRUKER

    override fun håndter(
        hendelse: Hendelse,
        person: Person,
        action: (Status) -> Unit,
    ): Status {
        when (hendelse) {
            is DagpengerMeldegruppeHendelse -> person.meldegruppe = hendelse.meldegruppeKode
            is AnnenMeldegruppeHendelse -> person.meldegruppe = hendelse.meldegruppeKode
            is MeldepliktHendelse -> person.meldeplikt = hendelse.statusMeldeplikt
            is ArbeidssøkerperiodeHendelse -> hendelse.håndter(person)
            else -> Unit
        }
        return Status.oppdater(person, this, action)
    }
}

data object IkkeDagpengerbruker : Status {
    override val type = Type.IKKE_DAGPENGERBRUKER

    override fun håndter(
        hendelse: Hendelse,
        person: Person,
        action: (Status) -> Unit,
    ): Status {
        when (hendelse) {
            is DagpengerMeldegruppeHendelse -> person.meldegruppe = hendelse.meldegruppeKode
            is AnnenMeldegruppeHendelse -> person.meldegruppe = hendelse.meldegruppeKode
            is MeldepliktHendelse -> person.meldeplikt = hendelse.statusMeldeplikt
            is ArbeidssøkerperiodeHendelse -> hendelse.håndter(person)

            else -> Unit
        }
        return Status.oppdater(person, this, action)
    }
}
