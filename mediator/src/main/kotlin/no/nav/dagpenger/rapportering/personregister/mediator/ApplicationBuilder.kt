package no.nav.dagpenger.rapportering.personregister.mediator

import no.nav.dagpenger.rapportering.personregister.mediator.api.internalApi
import no.nav.dagpenger.rapportering.personregister.mediator.api.konfigurasjon
import no.nav.dagpenger.rapportering.personregister.mediator.api.personstatusApi
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresDataSourceBuilder.runMigration
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.tjenester.SøknadMottak
import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection

internal class ApplicationBuilder(
    configuration: Map<String, String>,
) : RapidsConnection.StatusListener {
    private val personRepository = PostgresPersonRepository(dataSource)
    private val personstatusMediator = PersonstatusMediator(personRepository)
    private val rapidsConnection =
        RapidApplication
            .Builder(RapidApplication.RapidApplicationConfig.fromEnv(configuration))
            .withKtorModule {
                konfigurasjon()
                internalApi()
                personstatusApi(personRepository)
            }.build()
            .apply {
                SøknadMottak(this, personstatusMediator)
            }

    init {
        rapidsConnection.register(this)
    }

    internal fun start() {
        rapidsConnection.start()
    }

    override fun onStartup(rapidsConnection: RapidsConnection) {
        runMigration()
    }
}
