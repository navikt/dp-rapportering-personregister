package no.nav.dagpenger.rapportering.personregister.modell.hendelser

import io.github.oshai.kotlinlogging.KotlinLogging
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Kildesystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.oppfyllerKrav
import no.nav.dagpenger.rapportering.personregister.modell.sendFrasigelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendOvertakelsesmelding
import no.nav.dagpenger.rapportering.personregister.modell.sendStartMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.sendStoppMeldingTilMeldekortregister
import no.nav.dagpenger.rapportering.personregister.modell.utils.erDatoIFortid
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import java.time.LocalDateTime
import java.time.LocalDateTime.now
import java.util.UUID

private val logger = KotlinLogging.logger {}
private val sikkerLogg = KotlinLogging.logger("tjenestekall")

data class VedtakHendelse(
    override val korrelasjonsId: UUID,
    override val ident: String,
    override val dato: LocalDateTime = now(),
    override val startDato: LocalDateTime,
    override val referanseId: String,
    override val sluttDato: LocalDateTime? = null,
    val utfall: Boolean,
    val behandlingskjedeId: String?,
) : Hendelse {
    override val kilde: Kildesystem = Kildesystem.PJ

    companion object {
        private const val PREFIKS_FREMTIDIG_START = "FREMTIDIG-START"
        private const val PREFIKS_FREMTIDIG_STANS = "FREMTIDIG-STANS"

        fun medFremtidigStart(
            korrelasjonsId: UUID,
            ident: String,
            dato: LocalDateTime = now(),
            startDato: LocalDateTime,
            referanseId: String,
            sluttDato: LocalDateTime? = null,
            utfall: Boolean,
            behandlingskjedeId: String?,
        ) = VedtakHendelse(
            korrelasjonsId = korrelasjonsId,
            ident = ident,
            dato = dato,
            startDato = startDato,
            referanseId = "$PREFIKS_FREMTIDIG_START-$referanseId",
            sluttDato = sluttDato,
            utfall = utfall,
            behandlingskjedeId = behandlingskjedeId,
        )

        fun medFremtidigStans(
            korrelasjonsId: UUID,
            ident: String,
            dato: LocalDateTime = now(),
            startDato: LocalDateTime,
            referanseId: String,
            sluttDato: LocalDateTime,
            utfall: Boolean,
            behandlingskjedeId: String?,
        ) = VedtakHendelse(
            korrelasjonsId = korrelasjonsId,
            ident = ident,
            dato = dato,
            startDato = startDato,
            referanseId = "$PREFIKS_FREMTIDIG_STANS-$referanseId",
            sluttDato = sluttDato,
            utfall = utfall,
            behandlingskjedeId = behandlingskjedeId,
        )
    }

    override fun behandle(person: Person) {
        person.hendelser.add(this)

        person.setAnsvarligSystem(AnsvarligSystem.DP)

        if (utfall && !erFremtidigStansHendelse()) {
            logger.info {
                "Sender Start-melding for vedtakshendelse med referanseId=$referanseId, startDato=$startDato, sluttDato=$sluttDato, utfall=$utfall"
            }
            sikkerLogg.info {
                "Sender Start-melding for vedtakshendelse med referanseId=$referanseId, startDato=$startDato, sluttDato=$sluttDato, utfall=$utfall, ident=$ident"
            }

            person.setHarRettTilDp(true)
            person.sendStartMeldingTilMeldekortregister(
                fraOgMed = startDato,
                tilOgMed = sluttDato,
                korrelasjonsId = korrelasjonsId,
            )
        }

        if (!utfall || sluttDato.erDatoIFortid()) {
            logger.info {
                "Sender Stopp-melding for vedtakshendelse med referanseId=$referanseId, startDato=$startDato, sluttDato=$sluttDato, utfall=$utfall"
            }
            sikkerLogg.info {
                "Sender Stopp-melding for vedtakshendelse med referanseId=$referanseId, startDato=$startDato, sluttDato=$sluttDato, utfall=$utfall, ident=$ident"
            }

            person.setHarRettTilDp(false)
            person.sendStoppMeldingTilMeldekortregister(
                fraOgMed = startDato,
                tilOgMed = sluttDato,
                harRett = utfall,
                korrelasjonsId = korrelasjonsId,
            )
        }

        person
            .vurderNyStatus()
            .takeIf { it != person.status }
            ?.let { status ->
                person.setStatus(status)
                if (person.oppfyllerKrav) {
                    logger.info {
                        "Sender overtakelsesmelding"
                    }
                    sikkerLogg.info {
                        "Sender overtakelsesmelding for person med ident=$ident"
                    }
                    person.sendOvertakelsesmelding(korrelasjonsId)
                } else {
                    person.arbeidssøkerperioder.gjeldende
                        ?.let { periode -> person.sendFrasigelsesmelding(periode.periodeId, false, korrelasjonsId) }
                }
            }
    }

    internal fun erFremtidigStansHendelse() = referanseId.startsWith(PREFIKS_FREMTIDIG_STANS)
}
