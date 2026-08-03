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
) = DagpengerMeldegruppeHendelse(
    UUID.randomUUID().toString(),
    ident,
    dato,
    referanseId,
    startDato,
    sluttDato,
    "DAGP",
    true,
)

fun annenMeldegruppeHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    sluttDato: LocalDateTime? = null,
    referanseId: String = "123",
) = AnnenMeldegruppeHendelse(UUID.randomUUID().toString(), ident, dato, referanseId, startDato, sluttDato, "ARBS", true)

fun meldepliktHendelse(
    dato: LocalDateTime = nå,
    startDato: LocalDateTime = nå,
    sluttDato: LocalDateTime? = null,
    status: Boolean = false,
) = MeldepliktHendelse(UUID.randomUUID().toString(), ident, dato, "123", startDato, sluttDato, status, true)

fun startetArbeidssøkerperiodeHendelse(
    periodeId: UUID = UUID.randomUUID(),
    ident: String = "12345678901",
    startet: LocalDateTime = tidligere,
) = StartetArbeidssøkerperiodeHendelse(UUID.randomUUID().toString(), periodeId, ident, nå, startet)

fun avsluttetArbeidssøkerperiodeHendelse() =
    AvsluttetArbeidssøkerperiodeHendelse(UUID.randomUUID().toString(), periodeId, ident, tidligere, nå, nå)

fun vedtakHendelse(
    korrelasjonsId: String = UUID.randomUUID().toString(),
    dato: LocalDateTime = nå,
    startDato: LocalDateTime,
    sluttDato: LocalDateTime?,
    referanseId: String = "vedtak-123",
    utfall: Boolean = true,
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
    korrelasjonsId: String = UUID.randomUUID().toString(),
    dato: LocalDateTime = nå,
    startDato: LocalDateTime,
    sluttDato: LocalDateTime,
    referanseId: String = "vedtak-123",
    utfall: Boolean = true,
) = VedtakHendelse.medFremtidigStans(
    korrelasjonsId = korrelasjonsId,
    ident = ident,
    dato = dato,
    startDato = startDato,
    referanseId = referanseId,
    sluttDato = sluttDato,
    utfall = utfall,
)
