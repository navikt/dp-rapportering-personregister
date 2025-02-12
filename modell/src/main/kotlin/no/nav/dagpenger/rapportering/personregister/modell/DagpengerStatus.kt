package no.nav.dagpenger.rapportering.personregister.modell

sealed interface DagpengerStatus {
    val type: Type

    enum class Type {
        AKTIV,
        INAKTIV,
    }

    fun håndter(hendelse: Hendelse): DagpengerStatus

    companion object {
        fun rehydrer(type: String): DagpengerStatus =
            when (Type.valueOf(type)) {
                Type.AKTIV -> AKTIV
                Type.INAKTIV -> AKTIV
            }
    }
}

data object AKTIV : DagpengerStatus {
    override val type = DagpengerStatus.Type.AKTIV

    override fun håndter(hendelse: Hendelse): DagpengerStatus =
        when (hendelse) {
            is StansHendelse -> INAKTIV
            else -> this
        }
}

data object INAKTIV : DagpengerStatus {
    override val type = DagpengerStatus.Type.INAKTIV

    override fun håndter(hendelse: Hendelse): DagpengerStatus =
        when (hendelse) {
            is SøknadHendelse -> AKTIV
            else -> this
        }
}
