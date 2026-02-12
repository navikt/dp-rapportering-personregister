package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

data class AnnenMeldegruppeHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    override val sluttDato: LocalDateTime?,
    val meldegruppeKode: String,
    val harMeldtSeg: Boolean,
) : MeldegruppeHendelse {
    override val kilde: Kildesystem = Kildesystem.Arena

    override fun behandle(person: Person) {
        if (gjelderTilbakeITid(person)) {
            logger.warn { "AnnenMeldegruppeHendelse med referanseId $referanseId gjelder tilbake i tid. Ignorerer." }
            person.hendelser.add(this)
            return
        }

        person.hendelser.add(this)
        person.setMeldegruppe(meldegruppeKode)

        person
            .vurderNyStatus()
            .takeIf { it != person.status && !person.oppfyllerKrav }
            ?.also {
                person.setStatus(it)
                person.arbeidssøkerperioder.gjeldende
                    ?.also { periode ->
                        person.sendFrasigelsesmelding(periode.periodeId, !harMeldtSeg)
                    }
            }
    }
}
