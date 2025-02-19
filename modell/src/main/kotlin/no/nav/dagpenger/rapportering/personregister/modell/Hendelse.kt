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

    override fun toString(): String = "Hendelse(ident='$ident', dato=$dato, kilde=$kilde, referanseId='$referanseId')"
}

class SøknadHendelse(
    ident: String,
    dato: LocalDateTime,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Søknad

    override fun toString(): String = "SøknadHendelse(ident='$ident', dato=$dato, kilde=$kilde, referanseId='$referanseId')"
}

class DagpengerMeldegruppeHendelse(
    ident: String,
    dato: LocalDateTime,
    val meldegruppeKode: String,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Arena

    override fun toString(): String =
        "DagpengerMeldegruppeHendelse(ident='$ident', dato=$dato, kilde=$kilde, referanseId='$referanseId', meldegruppeKode='$meldegruppeKode')"
}

class AnnenMeldegruppeHendelse(
    ident: String,
    dato: LocalDateTime,
    val meldegruppeKode: String,
    override val referanseId: String,
) : Hendelse(ident = ident, dato = dato) {
    override val kilde = Kildesystem.Arena

    override fun toString(): String =
        "AnnenMeldegruppeHendelse(ident='$ident', dato=$dato, kilde=$kilde, referanseId='$referanseId', meldegruppeKode='$meldegruppeKode')"
}

class ArbeidssøkerHendelse(
    ident: String,
    val periodeId: UUID,
    val startDato: LocalDateTime,
    val sluttDato: LocalDateTime? = null,
) : Hendelse(ident = ident, dato = startDato) {
    override val kilde = Kildesystem.Arbeidssokerregisteret
    override val referanseId = periodeId.toString()

    override fun toString(): String =
        "ArbeidssøkerHendelse(ident='$ident', periodeId=$periodeId, startDato=$startDato, sluttDato=$sluttDato, kilde=$kilde, referanseId='$referanseId')"
}

enum class Kildesystem {
    Søknad,
    Arena,
    Arbeidssokerregisteret,
}
