package no.nav.dagpenger.rapportering.personregister.modell

import mu.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

enum class Status {
    DAGPENGERBRUKER,
    IKKE_DAGPENGERBRUKER,
}

data class Person(
    val ident: String,
    val statusHistorikk: TemporalCollection<Status> = TemporalCollection(),
    val arbeidssøkerperioder: MutableList<Arbeidssøkerperiode> = mutableListOf(),
) {
    var meldegruppe: String? = null
    var meldeplikt: Boolean = false

    val hendelser = mutableListOf<Hendelse>()

    val observers = mutableListOf<PersonObserver>()

    fun addObserver(observer: PersonObserver) {
        observers.add(observer)
    }

    init {
        if (statusHistorikk.isEmpty()) {
            statusHistorikk.put(LocalDateTime.now(), IKKE_DAGPENGERBRUKER)
        }
    }

    fun status(dato: LocalDateTime): Status = statusHistorikk.get(dato)

    val status: Status
        get() = status(LocalDateTime.now())

    fun setStatus(nyStatus: Status) {
        if (nyStatus !== status) {
            statusHistorikk.put(LocalDateTime.now(), nyStatus)
        }
    }

    fun behandle(hendelse: Hendelse) {
        hendelser.add(hendelse)
        hendelse.behandle(this)
    }
}

fun Person.overtaArbeidssøkerBekreftelse() {
    logger.info("Overtar arbeidssøkerbekreftelse")
    arbeidssøkerperioder.gjeldende?.let {
        logger.info("Fant gjeldende arbeidssøkerperiode med periodeId ${it.periodeId}")
        if (it.overtattBekreftelse != true) {
            logger.info("Gjeldende arbeidssøkerperiode har ikke overtatt bekreftelse.")
            try {
                logger.info("Antall observere: ${observers.size}")
                observers.forEach { observer -> observer.overtaArbeidssøkerBekreftelse(this) }
                logger.info("Kjørte overtagelse på observere uten feil. Setter overtattBekreftelse=true")
                it.overtattBekreftelse = true
            } catch (e: Exception) {
                logger.error(e) { "Overtagelse feilet!" }
                it.overtattBekreftelse = false
                throw e
            }
        }
    }
}

fun Person.frasiArbeidssøkerBekreftelse(
    periodeId: UUID,
    fristBrutt: Boolean,
    periodeAvsluttet: Boolean = false,
) {
    arbeidssøkerperioder
        .find { it.periodeId == periodeId }
        ?.let {
            if (it.overtattBekreftelse == true) {
                try {
                    if (!periodeAvsluttet) {
                        observers.forEach { observer -> observer.frasiArbeidssøkerBekreftelse(this, fristBrutt) }
                    }
                    it.overtattBekreftelse = false
                } catch (e: Exception) {
                    it.overtattBekreftelse = true
                    throw e
                }
            }
        }
}

fun Person.leggTilNyArbeidssøkerperiode(hendelse: StartetArbeidssøkerperiodeHendelse) {
    arbeidssøkerperioder.add(
        Arbeidssøkerperiode(
            hendelse.periodeId,
            ident,
            hendelse.startet,
            null,
            overtattBekreftelse = null,
        ),
    )
}

fun Person.leggTilNyArbeidssøkerperiode(hendelse: AvsluttetArbeidssøkerperiodeHendelse) {
    arbeidssøkerperioder.add(
        Arbeidssøkerperiode(
            hendelse.periodeId,
            ident,
            hendelse.startet,
            hendelse.avsluttet,
            overtattBekreftelse = false,
        ),
    )
}

val Person.erArbeidssøker: Boolean
    get() = arbeidssøkerperioder.gjeldende != null

val Person.overtattBekreftelse: Boolean
    get() = arbeidssøkerperioder.gjeldende?.overtattBekreftelse ?: false

fun Person.vurderNyStatus() = if (this.oppfyllerKrav) DAGPENGERBRUKER else IKKE_DAGPENGERBRUKER

val Person.oppfyllerKrav: Boolean get() = this.erArbeidssøker && this.meldeplikt && this.meldegruppe == "DAGP"
