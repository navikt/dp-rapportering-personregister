package no.nav.dagpenger.rapportering.personregister.mediator.utils

import io.micrometer.core.instrument.Clock
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import io.prometheus.metrics.model.registry.PrometheusRegistry
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ActionTimer
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.ArbeidssøkerperiodeMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.BehandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldegruppeendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldepliktendringMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldestatusMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.MeldesyklusErPassertMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SynkroniserPersonMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.SøknadMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.metrikker.VedtakMetrikker

object MetrikkerTestUtil {
    private val meterRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT, PrometheusRegistry.defaultRegistry, Clock.SYSTEM)
    val søknadMetrikker = SøknadMetrikker(meterRegistry)
    val behandlingsresultatMetrikker = BehandlingsresultatMetrikker(meterRegistry)
    val meldestatusMetrikker = MeldestatusMetrikker(meterRegistry)
    val meldegruppeendringMetrikker = MeldegruppeendringMetrikker(meterRegistry)
    val meldepliktendringMetrikker = MeldepliktendringMetrikker(meterRegistry)
    val meldesyklusErPassertMetrikker = MeldesyklusErPassertMetrikker(meterRegistry)
    val arbeidssøkerperiodeMetrikker = ArbeidssøkerperiodeMetrikker(meterRegistry)
    val synkroniserPersonMetrikker = SynkroniserPersonMetrikker(meterRegistry)
    val vedtakMetrikker = VedtakMetrikker(meterRegistry)
    val actionTimer = ActionTimer(meterRegistry)
}
