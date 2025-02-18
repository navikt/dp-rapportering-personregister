package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

sealed class Hendelse(
    val ident: String,
    val dato: LocalDateTime,
) {
    abstract val kilde: Kildesystem
    abstract val referanseId: String

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Hendelse) return false

        if (ident != other.ident) return false
        if (dato != other.dato) return false
        if (kilde != other.kilde) return false
        if (referanseId != other.referanseId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = ident.hashCode()
        result = 31 * result + dato.hashCode()
        result = 31 * result + kilde.hashCode()
        result = 31 * result + referanseId.hashCode()
        return result
    }
}

class SøknadHendelse(
    ident: String,
    dato: LocalDateTime,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Søknad
}

class DagpengerMeldegruppeHendelse(
    ident: String,
    dato: LocalDateTime,
    val meldegruppeKode: String,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Arena
}

class AnnenMeldegruppeHendelse(
    ident: String,
    dato: LocalDateTime,
    val meldegruppeKode: String,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Arena
}

class ArbeidssøkerHendelse(
    ident: String,
    val periodeId: UUID,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime? = null,
) : Hendelse(ident = ident, dato = startDato) {
    override val kilde = Kildesystem.Arbeidssokerregisteret
    override val referanseId = periodeId.toString()
}

enum class Kildesystem {
    Søknad,
    Arena,
    Arbeidssokerregisteret,
}
