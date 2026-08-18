package no.nav.dagpenger.rapportering.personregister.modell

import java.time.LocalDateTime
import java.util.UUID

interface PersonObserver {
    fun sendOvertakelsesmelding(
        person: Person,
        korrelasjonsId: UUID,
    ) {}

    fun sendFrasigelsesmelding(
        person: Person,
        fristBrutt: Boolean = false,
        korrelasjonsId: UUID,
    ) {
    }

    fun overtattArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {
    }

    fun frasagtArbeidssøkerbekreftelse(
        person: Person,
        periodeId: UUID,
    ) {
    }

    fun sendStartMeldingTilMeldekortregister(
        person: Person,
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
        skalMigreres: Boolean,
        korrelasjonsId: UUID,
    ) {}

    fun sendStoppMeldingTilMeldekortregister(
        person: Person,
        fraOgMed: LocalDateTime,
        tilOgMed: LocalDateTime?,
        harRett: Boolean,
        korrelasjonsId: UUID,
    ) {}
}
