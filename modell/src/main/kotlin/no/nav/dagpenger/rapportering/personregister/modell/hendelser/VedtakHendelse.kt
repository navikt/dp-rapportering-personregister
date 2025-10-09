package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.VedtakType
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.sendStoppMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

data class VedtakHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val startDato: LocalDateTime,
    override val referanseId: String,
    val sluttDato: LocalDateTime? = null,
    val utfall: Boolean,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        // Starter eller stopper meldekortproduksjon basert på vedtakets utfall
        if (utfall) {
            person.setAnsvarligSystem(AnsvarligSystem.DP)

            person.setVedtak(VedtakType.INNVILGET)
            person.sendStartMeldingTilMeldekortregister(startDato = startDato)
        } else {
            person.setVedtak(VedtakType.STANSET)
            if (person.ansvarligSystem == AnsvarligSystem.DP) {
                person.sendStoppMeldingTilMeldekortregister(stoppDato = startDato)
            }
        }

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let { status ->
                person.setStatus(status)
                if (person.oppfyllerKrav) {
                    person.sendOvertakelsesmelding()
                } else {
                    // Sjekker om meldekortregisteret har meldt at bruker har brutt fristen for meldeplikten etter startDato
                    val fristBrutt =
                        person.hendelser
                            .filterIsInstance<MeldesyklusErPassertHendelse>()
                            .any {
                                it.dato.isAfter(startDato)
                            }

                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, fristBrutt = fristBrutt) }
                }
            }
    }
}
