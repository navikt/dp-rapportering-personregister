package no.nav.dagpenger.rapportering.personregister.mediator

import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.konfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.clean
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.VedtakMottak
import no.nav.helse.rapids_rivers.RapidApplication

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val personRepository = PostgresPersonRepository(dataSource)
    private val personstatusMediator = PersonstatusMediator(personRepository)
    private val rapidsConnection =
        RapidApplication
            .create(configuration) { engine, rapid ->
                with(engine.application) {
                    konfigurasjon()
                    internalApi()
                    personstatusApi(personRepository)
                }
                SøknadMottak(rapid, personstatusMediator)
                VedtakMottak(rapid, personstatusMediator)
            }

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        clean()
        runMigration()
    }
}
