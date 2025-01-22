package no.nav.dagpenger.rapportering.personregister.modell

sealed interface Status {
    val type: Type

    enum class Type {
        SØKT,
        INNVILGET,
        AVSLÅTT,
        STANSET,
    }

    companion object {
        fun rehydrer(type: String): Status =
            when (Type.valueOf(type)) {
                Type.SØKT -> SØKT
                Type.INNVILGET -> INNVILGET
                Type.AVSLÅTT -> AVSLÅTT
                Type.STANSET -> STANSET
                else -> SØKT
            }
    }

    fun håndter(hendelse: Hendelse): Status
}

data object SØKT : Status {
    override val type = Status.Type.SØKT

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse) {
            is InnvilgelseHendelse -> INNVILGET
            is AvslagHendelse -> AVSLÅTT
            else -> this
        }
}

object INNVILGET : Status {
    override val type = Status.Type.INNVILGET

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse) {
            is StansHendelse -> STANSET
            else -> this
        }
}

data object AVSLÅTT : Status {
    override val type = Status.Type.AVSLÅTT

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse) {
            is InnvilgelseHendelse -> INNVILGET
            is SøknadHendelse -> SØKT
            else -> this
        }
}

data object STANSET : Status {
    override val type = Status.Type.STANSET

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse) {
            is InnvilgelseHendelse -> INNVILGET
            else -> this
        }
}
