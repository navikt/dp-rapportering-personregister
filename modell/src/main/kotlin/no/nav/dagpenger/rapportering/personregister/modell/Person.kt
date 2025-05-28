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

fun Person.sendOvertakelsesmelding() {
    logger.info("Overtar arbeidssøkerbekreftelse")
    arbeidssøkerperioder.gjeldende?.let {
        logger.info("Fant gjeldende arbeidssøkerperiode med periodeId ${it.periodeId}")
        logger.info("Gjeldende arbeidssøkerperiode har ikke overtatt bekreftelse.")
        try {
            logger.info("Antall observere: ${observers.size}")
            observers.forEach { observer -> observer.sendOvertakelsesmelding(this) }
            logger.info("Kjørte overtagelse på observere uten feil")
        } catch (e: Exception) {
            logger.error(e) { "Overtagelse feilet!" }
            throw e
        }
    }
}

fun Person.merkPeriodeSomOvertatt(periodeId: UUID) {
    logger.info("Merker periode $periodeId som overtatt")
    arbeidssøkerperioder
        .find { it.periodeId == periodeId }
        ?.let { it.overtattBekreftelse = true }
        ?: logger.error { "Fant ikke periode $periodeId og klarte derfor ikke å markere perioden som overtatt " }
}

fun Person.sendFrasigelsesmelding(
    periodeId: UUID,
    fristBrutt: Boolean,
) {
    logger.info("Frasier arbeidssøkerbekreftelse")
    arbeidssøkerperioder
        .find { it.periodeId == periodeId }
        ?.let {
            if (it.overtattBekreftelse == true) {
                logger.info("Gjeldende arbeidssøkerperiode har overtatt bekreftelse.")
                try {
                    logger.info("Antall observere: ${observers.size}")
                    observers.forEach { observer -> observer.sendFrasigelsesmelding(this, fristBrutt) }
                    logger.info("Kjørte frasigelse på observere uten feil")
                } catch (e: Exception) {
                    logger.error(e) { "Frasigelse feilet!" }
                    throw e
                }
            }
        }
}

fun Person.merkPeriodeSomIkkeOvertatt(periodeId: UUID) {
    logger.info("Merker periode $periodeId som ikke overtatt")
    arbeidssøkerperioder
        .find { it.periodeId == periodeId }
        ?.let { it.overtattBekreftelse = false }
        ?: logger.error { "Fant ikke periode $periodeId og klarte derfor ikke å markere perioden som ikke overtatt" }
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
