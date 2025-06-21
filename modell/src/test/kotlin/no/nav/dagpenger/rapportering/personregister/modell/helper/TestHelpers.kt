package no.nav.dagpenger.rapportering.personregister.modell.helper

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import java.time.LocalDateTime
import java.util.UUID

val ident = "12345678901"
val nå: LocalDateTime = LocalDateTime.now()
val tidligere: LocalDateTime = nå.minusDays(1)
val periodeId: UUID = UUID.randomUUID()

fun testPerson(block: Person.() -> Unit) {
    Person(ident).apply(block)
}

fun arbeidssøker(
    overtattBekreftelse: Boolean = false,
    block: Person.() -> Unit,
) {
    Person(ident)
        .apply {
            arbeidssøkerperioder.add(
                Arbeidssøkerperiode(
                    periodeId,
                    ident,
                    LocalDateTime.now(),
                    null,
                    overtattBekreftelse = overtattBekreftelse,
                ),
            )
        }.apply(block)
}

fun dagpengerMeldegruppeHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    referanseId: String = "123",
) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato, null, "DAGP", true)

fun annenMeldegruppeHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    referanseId: String = "123",
) = AnnenMeldegruppeHendelse(ident, dato, referanseId, startDato, null, "ARBS", true)

fun meldepliktHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    status: Boolean = false,
) = MeldepliktHendelse(ident, dato, "123", startDato, null, status, true)

fun startetArbeidssøkerperiodeHendelse(
    periodeId: UUID = UUID.randomUUID(),
    ident: String = "12345678901",
    startet: LocalDateTime = tidligere,
) = StartetArbeidssøkerperiodeHendelse(periodeId, ident, startet)

fun avsluttetArbeidssøkerperiodeHendelse() = AvsluttetArbeidssøkerperiodeHendelse(periodeId, ident, tidligere, nå)
