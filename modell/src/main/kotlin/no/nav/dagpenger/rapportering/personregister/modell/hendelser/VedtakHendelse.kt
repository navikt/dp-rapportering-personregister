package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.sendStoppMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalDateTime.now

data class VedtakHendelse(
    override val ident: String,
    override val dato: LocalDateTime = now(),
    override val startDato: LocalDateTime,
    override val referanseId: String,
    override val sluttDato: LocalDateTime? = null,
    val utfall: Boolean,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        if (skalStarteDagpenger) {
            startDagpenger(person)
        } else {
            avsluttDagpenger(person)
        }

        oppdaterStatus(person)
    }

    private val skalStarteDagpenger: Boolean
        get() = utfall && !sluttDatoErPassert

    private val erInnvilgetTilbakeITid: Boolean
        get() = utfall && sluttDatoErPassert

    private val sluttDatoErPassert: Boolean
        get() = sluttDato?.toLocalDate()?.isBefore(LocalDate.now()) ?: false

    private fun startDagpenger(person: Person) {
        val skalMigreres = person.ansvarligSystem != AnsvarligSystem.DP
        person.setAnsvarligSystem(AnsvarligSystem.DP)
        person.setHarRettTilDp(true)
        person.sendStartMeldingTilMeldekortregister(fraOgMed = startDato, tilOgMed = sluttDato, skalMigreres = skalMigreres)
    }

    private fun avsluttDagpenger(person: Person) {
        person.setHarRettTilDp(false)

        if (erInnvilgetTilbakeITid && person.ansvarligSystem != AnsvarligSystem.DP) {
            person.setAnsvarligSystem(AnsvarligSystem.DP)
        }

        if (person.ansvarligSystem == AnsvarligSystem.DP) person.stoppMeldekortproduksjon()
    }

    private fun Person.stoppMeldekortproduksjon() = sendStoppMeldingTilMeldekortregister(fraOgMed = startDato, tilOgMed = sluttDato)

    private fun oppdaterStatus(person: Person) {
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
