package no.nav.dagpenger.rapportering.personregister.mediator.connector

import no.nav.dagpenger.rapportering.personregister.mediator.ZONE_ID
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.ArbeidssøkerBekreftelseMelding
import no.nav.paw.bekreftelse.melding.v1.Bekreftelse
import no.nav.paw.bekreftelse.melding.v1.vo.Bekreftelsesloesning
import no.nav.paw.bekreftelse.melding.v1.vo.Bruker
import no.nav.paw.bekreftelse.melding.v1.vo.BrukerType
import no.nav.paw.bekreftelse.melding.v1.vo.Metadata
import no.nav.paw.bekreftelse.melding.v1.vo.Svar
import java.time.Instant

class BekreftelseMapper {
    companion object {
        fun tilBekreftelse(arbeidssøkerBekreftelseMelding: ArbeidssøkerBekreftelseMelding): Bekreftelse {
            val svar = arbeidssøkerBekreftelseMelding.bekreftelse.svar
            val bruker = svar.sendtInnAv.utførtAv

            return Bekreftelse(
                arbeidssøkerBekreftelseMelding.bekreftelse.periodeId,
                Bekreftelsesloesning.DAGPENGER,
                arbeidssøkerBekreftelseMelding.bekreftelse.id,
                Svar(
                    Metadata(
                        Instant.now().atZone(ZONE_ID).toInstant(),
                        Bruker(
                            BrukerType.valueOf(bruker.type),
                            bruker.ident,
                            bruker.sikkerhetsnivå,
                        ),
                        Bekreftelsesloesning.DAGPENGER.name,
                        svar.sendtInnAv.årsak,
                    ),
                    svar.gjelderFra.atZone(ZONE_ID).toInstant(),
                    svar.gjelderTil
                        .plusDays(1)
                        .atZone(ZONE_ID)
                        .toInstant(),
                    svar.harJobbetIDennePerioden,
                    svar.vilFortsetteSomArbeidssøker,
                ),
            )
        }
    }
}
