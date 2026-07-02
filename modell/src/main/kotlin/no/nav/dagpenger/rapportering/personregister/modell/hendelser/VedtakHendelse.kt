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

    companion object {
        private const val FREMTIDIG_START_SUFFIKS = "FREMTIDIG-START"
        private const val FREMTIDIG_STANS_SUFFIKS = "FREMTIDIG-STANS"

        fun medFremtidigStart(
            ident: String,
            dato: LocalDateTime = now(),
            startDato: LocalDateTime,
            referanseId: String,
            sluttDato: LocalDateTime? = null,
            utfall: Boolean,
        ) = VedtakHendelse(
            ident = ident,
            dato = dato,
            startDato = startDato,
            referanseId = "$referanseId-$FREMTIDIG_START_SUFFIKS",
            sluttDato = sluttDato,
            utfall = utfall,
        )

        fun medFremtidigStans(
            ident: String,
            dato: LocalDateTime = now(),
            startDato: LocalDateTime,
            referanseId: String,
            sluttDato: LocalDateTime? = null,
            utfall: Boolean,
        ) = VedtakHendelse(
            ident = ident,
            dato = dato,
            startDato = startDato,
            referanseId = "$referanseId-$FREMTIDIG_STANS_SUFFIKS",
            sluttDato = sluttDato,
            utfall = utfall,
        )
    }

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        if (utfall && !erFremtidigStansHendelse()) {
            val skalMigreres = person.ansvarligSystem != AnsvarligSystem.DP
            person.setAnsvarligSystem(AnsvarligSystem.DP)

            person.setHarRettTilDp(true)
            person.sendStartMeldingTilMeldekortregister(
                fraOgMed = startDato,
                tilOgMed = sluttDato,
                skalMigreres = skalMigreres,
            )
        }

        if (!utfall || sluttDato.erFortid()) {
            person.setHarRettTilDp(false)
            if (person.ansvarligSystem == AnsvarligSystem.DP) {
                person.sendStoppMeldingTilMeldekortregister(fraOgMed = startDato, tilOgMed = sluttDato)
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

    fun erFremtidigStansHendelse() = referanseId.endsWith(FREMTIDIG_STANS_SUFFIKS)
}

private fun LocalDateTime?.erFortid() = this?.toLocalDate()?.isBefore(LocalDate.now()) ?: false
