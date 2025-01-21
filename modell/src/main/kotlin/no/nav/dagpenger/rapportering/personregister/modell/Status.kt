package no.nav.dagpenger.rapportering.personregister.modell

sealed interface Status {
    val type: Type

    enum class Type {
        SØKT,
        INNVILGET,
        AVSLÅTT,
        STANSET,
    }

    fun håndter(hendelse: Hendelse): Status
}

data object SØKT : Status {
    override val type = Status.Type.SØKT

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse.status) {
            Status.Type.INNVILGET -> INNVILGET
            Status.Type.AVSLÅTT -> AVSLÅTT
            else -> this
        }
}

object INNVILGET : Status {
    override val type = Status.Type.INNVILGET

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse.status) {
            Status.Type.STANSET -> STANSET
            else -> this
        }
}

data object AVSLÅTT : Status {
    override val type = Status.Type.AVSLÅTT

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse.status) {
            Status.Type.INNVILGET -> INNVILGET
            Status.Type.SØKT -> SØKT
            else -> this
        }
}

data object STANSET : Status {
    override val type = Status.Type.STANSET

    override fun håndter(hendelse: Hendelse): Status =
        when (hendelse.status) {
            Status.Type.INNVILGET -> INNVILGET
            else -> this
        }
}
