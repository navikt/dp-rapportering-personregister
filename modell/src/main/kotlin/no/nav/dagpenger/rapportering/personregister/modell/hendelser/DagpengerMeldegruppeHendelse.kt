package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

data class DagpengerMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    override val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean,
    override val kilde: Kildesystem = Kildesystem.Arena,
) : MeldegruppeHendelse {
    override fun behandle(person: Person) {
        if (gjelderTilbakeITid(person)) {
            logger.warn { "DagpengerMeldegruppeHendelse med referanseId $referanseId gjelder tilbake i tid. Ignorerer." }
            person.hendelser.add(this)
            return
        }

        person.hendelser.add(this)
        person.setMeldegruppe(meldegruppeKode)

        person
            .vurderNyStatus()
            .takeIf { it != person.status && person.oppfyllerKrav }
            ?.also {
                person.setStatus(it)
                person.sendOvertakelsesmelding()
            }
    }
}
