package no.nav.dagpenger.rapportering.personregister.modell.helper

import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AvsluttetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
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
    sluttDato: LocalDateTime? = null,
    referanseId: String = "123",
    korrelasjonsId: UUID? = null,
) = DagpengerMeldegruppeHendelse(korrelasjonsId, ident, dato, referanseId, startDato, sluttDato, "DAGP", true)

fun annenMeldegruppeHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    sluttDato: LocalDateTime? = null,
    referanseId: String = "123",
    korrelasjonsId: UUID? = null,
) = AnnenMeldegruppeHendelse(korrelasjonsId, ident, dato, referanseId, startDato, sluttDato, "ARBS", true)

fun meldepliktHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    sluttDato: LocalDateTime? = null,
    status: Boolean = false,
    korrelasjonsId: UUID? = null,
) = MeldepliktHendelse(korrelasjonsId, ident, dato, "123", startDato, sluttDato, status, true)

fun startetArbeidssøkerperiodeHendelse(
    periodeId: UUID = UUID.randomUUID(),
    ident: String = "12345678901",
    startet: LocalDateTime = tidligere,
    korrelasjonsId: UUID? = null,
) = StartetArbeidssøkerperiodeHendelse(korrelasjonsId, periodeId, ident, nå, startet)

fun avsluttetArbeidssøkerperiodeHendelse() = AvsluttetArbeidssøkerperiodeHendelse(null, periodeId, ident, tidligere, nå, nå)

fun vedtakHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime,
    sluttDato: LocalDateTime?,
    referanseId: String = "vedtak-123",
    utfall: Boolean = true,
    korrelasjonsId: UUID? = null,
) = VedtakHendelse(
    korrelasjonsId = korrelasjonsId,
    ident = ident,
    dato = dato,
    startDato = startDato,
    referanseId = referanseId,
    sluttDato = sluttDato,
    utfall = utfall,
)

fun vedtakHendelseMedFremtidigStans(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime,
    sluttDato: LocalDateTime,
    referanseId: String = "vedtak-123",
    utfall: Boolean = true,
    korrelasjonsId: UUID? = null,
) = VedtakHendelse.medFremtidigStans(
    korrelasjonsId = korrelasjonsId,
    ident = ident,
    dato = dato,
    startDato = startDato,
    referanseId = referanseId,
    sluttDato = sluttDato,
    utfall = utfall,
)
