package no.nav.dagpenger.rapportering.personregister.modell

sealed interface Status {
    val type: Type

    enum class Type {
        DAGPENGERBRUKER,
        IKKE_DAGPENGERBRUKER,
    }

    fun håndter(
        hendelse: Hendelse,
        action: (Status) -> Unit,
    ): Status

    companion object {
        fun rehydrer(type: String): Status =
            when (Type.valueOf(type)) {
                Type.DAGPENGERBRUKER -> Dagpengerbruker
                Type.IKKE_DAGPENGERBRUKER -> IkkeDagpengerbruker
            }
    }
}

data object Dagpengerbruker : Status {
    override val type = Status.Type.DAGPENGERBRUKER

    override fun håndter(
        hendelse: Hendelse,
        action: (Status) -> Unit,
    ): Status =
        when (hendelse) {
            is AnnenMeldegruppeHendelse -> IkkeDagpengerbruker.also(action)
            else -> this
        }
}

data object IkkeDagpengerbruker : Status {
    override val type = Status.Type.IKKE_DAGPENGERBRUKER

    override fun håndter(
        hendelse: Hendelse,
        action: (Status) -> Unit,
    ): Status =
        when (hendelse) {
            is SøknadHendelse,
            is DagpengerMeldegruppeHendelse,
            -> Dagpengerbruker.also(action)
            else -> this
        }
}
