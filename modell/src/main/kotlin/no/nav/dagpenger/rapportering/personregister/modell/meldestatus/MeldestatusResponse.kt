package no.nav.dagpenger.rapportering.personregister.modell.meldestatus

import java.time.LocalDateTime

data class MeldestatusResponse(
    val arenaPersonId: Long,
    val personIdent: String,
    val formidlingsgruppe: String,
    val harMeldtSeg: Boolean,
    val meldepliktListe: List<Meldeplikt>? = null,
    val meldegruppeListe: List<Meldegruppe>? = null,
) {
    data class Meldeplikt(
        val meldeplikt: Boolean,
        val meldepliktperiode: Periode? = null,
        val begrunnelse: String? = null,
        val endringsdata: Endring? = null,
    )

    data class Meldegruppe(
        val meldegruppe: String,
        val meldegruppeperiode: Periode? = null,
        val begrunnelse: String? = null,
        val endringsdata: Endring? = null,
    )

    data class Periode(
        val fom: LocalDateTime,
        val tom: LocalDateTime? = null,
    )

    data class Endring(
        val registrertAv: String,
        val registreringsdato: LocalDateTime,
        val endretAv: String,
        val endringsdato: LocalDateTime,
    )
}
