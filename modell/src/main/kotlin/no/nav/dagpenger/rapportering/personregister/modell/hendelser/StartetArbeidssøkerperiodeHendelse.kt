package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.erArbeidssøker
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.leggTilNyArbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.overtattBekreftelse
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.util.UUID

private val logger = KotlinLogging.logger {}

data class StartetArbeidssøkerperiodeHendelse(
    override val korrelasjonsId: UUID,
    override val periodeId: UUID,
    override val ident: String,
    override val dato: LocalDateTime = LocalDateTime.now(),
    override val startDato: LocalDateTime,
) : ArbeidssøkerperiodeHendelse(periodeId) {
    override val sluttDato = null

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        oppdaterGjeldendeperiode(person, this)
        settDpRettOgSendStartmelding(person, korrelasjonsId)

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.also { person.setStatus(it) }
            ?.takeIf { !person.overtattBekreftelse }
            ?.also { person.sendOvertakelsesmelding(korrelasjonsId) }
    }
}

private fun settDpRettOgSendStartmelding(
    person: Person,
    korrelasjonsId: UUID,
) {
    if (person.ansvarligSystem == AnsvarligSystem.DP && person.erArbeidssøker && !person.harRettTilDp) {
        val nyesteSøknad = person.hendelser.filterIsInstance<SøknadHendelse>().maxByOrNull { it.startDato }

        if (nyesteSøknad == null) {
            logger.error {
                "Mangler søknad for bruker som har registrert seg som arbeidssøker, dette burde ikke skje og må undersøkes. " +
                    "Setter ikke harRettTilDp og starter ikke meldekortproduksjon." +
                    "periodeId=${person.arbeidssøkerperioder.gjeldende?.periodeId}, korrelasjonsId=$korrelasjonsId"
            }
            return
        }

        if (person.harVedtakSomGjelderPåEllerEtter(nyesteSøknad.startDato)) {
            logger.info {
                "Det finnes et vedtak som gjelder på eller etter brukerens nyeste søknad. " +
                    "Søknaden antas å være ferdigbehandlet tidligere og brukeren har ingen aktiv søknad.  " +
                    "Setter ikke harRettTilDp og starter ikke meldekortproduksjon." +
                    "periodeId=${person.arbeidssøkerperioder.gjeldende?.periodeId}, korrelasjonsId=$korrelasjonsId"
            }
            return
        }

        person.setHarRettTilDp(true)
        person.sendStartMeldingTilMeldekortregister(
            fraOgMed = nyesteSøknad.startDato,
            skalMigreres = false,
            korrelasjonsId = korrelasjonsId,
        )
    }
}

private fun Person.harVedtakSomGjelderPåEllerEtter(søknadStartDato: LocalDateTime): Boolean =
    hendelser.filterIsInstance<VedtakHendelse>().any { vedtak ->
        val gjelderPåSøknadsdato =
            vedtak.startDato <= søknadStartDato && (
                vedtak.sluttDato == null ||
                    !vedtak.sluttDato.isBefore(
                        søknadStartDato,
                    )
            )
        val heleVedtaketErEtterSøknadsdato = vedtak.startDato >= søknadStartDato

        gjelderPåSøknadsdato || heleVedtaketErEtterSøknadsdato
    }

private fun oppdaterGjeldendeperiode(
    person: Person,
    hendelse: StartetArbeidssøkerperiodeHendelse,
) {
    if (person.arbeidssøkerperioder.none { it.periodeId == hendelse.periodeId }) {
        person.arbeidssøkerperioder.gjeldende?.apply {
            logger.warn { "Personen har allerede en aktiv arbeidssøkerperiode. Avslutter den før vi starter en ny." }
            avsluttet = LocalDateTime.now()
            overtattBekreftelse = false
        }

        person.leggTilNyArbeidssøkerperiode(hendelse)
    }
}
