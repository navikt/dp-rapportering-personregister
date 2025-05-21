package no.nav.dagpenger.rapportering.personregister.modell

data class Ident(
    val ident: String,
    val gruppe: IdentGruppe,
    val historisk: Boolean,
) {
    enum class IdentGruppe {
        AKTORID,
        FOLKEREGISTERIDENT,
        NPID,
    }
}
