package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime

/**
 * Represents a combined event containing both MeldepliktHendelse and MeldegruppeHendelse (either DagpengerMeldegruppeHendelse or AnnenMeldegruppeHendelse).
 * This is used when messages from both topics are successfully joined based on FODSELSNR.
 */
data class KombinertHendelse(
    override val ident: String,
    override val dato: LocalDateTime,
    override val referanseId: String,
    val meldepliktHendelser: List<MeldepliktHendelse>,
    val meldegruppeHendelser: List<Hendelse>, // Can contain DagpengerMeldegruppeHendelse or AnnenMeldegruppeHendelse
    override val arenaId: Int? = null,
    override val kilde: Kildesystem = Kildesystem.Dagpenger
) : Hendelse {
    override fun behandle(person: Person) {
        // Process all meldepliktHendelser
        meldepliktHendelser.forEach { it.behandle(person) }
        
        // Process all meldegruppeHendelser
        meldegruppeHendelser.forEach { it.behandle(person) }
    }
}