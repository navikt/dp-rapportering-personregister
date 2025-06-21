package no.nav.dagpenger.rapportering.personregister.modell.helper

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import java.time.LocalDateTime
import java.util.UUID

private val ident = "12345678901"
val nå: LocalDateTime = LocalDateTime.now()
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
