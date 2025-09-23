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
    val søknadId: String,
    val utfall: Boolean,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        val søknadsdato =
            person.hendelser
                .filterIsInstance<SøknadHendelse>()
                .find { it.referanseId == søknadId }
                ?.dato
                ?: throw RuntimeException("Finner ikke søknad med id $søknadId. Klarte ikke å behandle vedtak.")

        // Starter eller stopper meldekortproduksjon bastert på vedtakets utfall
        if (utfall) {
            person.setAnsvarligSystem(AnsvarligSystem.DP)

            person.setVedtak(VedtakType.INNVILGET)
            person.sendStartMeldingTilMeldekortregister(startDato = søknadsdato)
        } else {
            person.setVedtak(VedtakType.AVSLÅTT) // TODO: Her må vi egentlig sjekke status på vedtaket
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
                    // Sjekker om meldekortregisteret har meldt at bruker har brutt fristen for meldeplikten etter søknadsdato
                    val fristBrutt =
                        person.hendelser
                            .filterIsInstance<MeldesyklusErPassertHendelse>()
                            .any {
                                it.dato.isAfter(søknadsdato)
                            }

                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, fristBrutt = fristBrutt) }
                }
            }
    }
}
