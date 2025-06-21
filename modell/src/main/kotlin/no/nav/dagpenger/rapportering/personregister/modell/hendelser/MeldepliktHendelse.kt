package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime

private val logger = KotlinLogging.logger {}

data class MeldepliktHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    override val startDato: LocalDateTime,
    val sluttDato: LocalDateTime?,
    val statusMeldeplikt: Boolean,
    val harMeldtSeg: Boolean,
    override val kilde: Kildesystem = Kildesystem.Arena,
) : Hendelse {
    override fun behandle(person: Person) {
        if (this.gjelderTilbakeITid(person)) {
            logger.warn { "MeldepliktHendelse med referanseId $referanseId gjelder tilbake i tid. Ignorerer." }
            person.hendelser.add(this)
            return
        }

        person.hendelser.add(this)
        person.setMeldeplikt(statusMeldeplikt)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let {
                person.setStatus(it)
                if (person.oppfyllerKrav) {
                    person.sendOvertakelsesmelding()
                } else {
                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, !harMeldtSeg) }
                }
            }
    }
}

private fun MeldepliktHendelse.gjelderTilbakeITid(person: Person) =
    person.hendelser
        .filterIsInstance<MeldepliktHendelse>()
        .maxByOrNull { it.startDato }
        ?.let { sisteMeldegruppeHendelse ->
            !this.startDato.isAfter(sisteMeldegruppeHendelse.startDato)
        } ?: false
