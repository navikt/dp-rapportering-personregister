package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import io.mockk.justRun
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.connector.createMockClient
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PostgresTempPersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPerson
import no.nav.dagpenger.rapportering.personregister.mediator.db.TempPersonStatus
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonObserver
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.vurderNyStatus
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class SendPaaVegneAvForAlleJobTest {
    private var personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val tempPersonRepository = PostgresTempPersonRepository(dataSource)
    private val observers = listOf(mockk<PersonObserver>())
    private val job = SendPaaVegneAvForAlleJob(createMockClient(200, "OK"))

    private val nå = LocalDateTime.now()
    private val ident1 = "12345678911"
    private val ident2 = "12345678912"
    private val ident3 = "12345678913"
    private val ident4 = "12345678914"
    private val ident5 = "12345678915"
    private val ident6 = "12345678916"
    private val identer =
        listOf(
            ident1,
            ident2,
            ident3,
            ident4,
            ident5,
            ident6,
        )

    @Test
    fun `sender paaVegneAvMelding for alle identer`() =
        withMigratedDb {
            val periodeId = UUID.randomUUID()
            identer.forEach { ident -> tempPersonRepository.lagrePerson(TempPerson(ident, TempPersonStatus.RETTET)) }
            identer.forEach { ident -> personRepository.lagrePerson(lagPerson(ident, periodeId)) }

            observers.forEach { observer ->
                justRun { observer.sendOvertakelsesmelding(any()) }
            }
            observers.forEach { observer ->
                justRun { observer.sendFrasigelsesmelding(any()) }
            }

            job.sendPaaVegneAv(identer, personRepository, tempPersonRepository, observers)

            identer.forEach {
                with(tempPersonRepository.hentPerson(it)!!) {
                    status shouldBe TempPersonStatus.FERDIGSTILT
                }
            }
        }

    private fun lagPerson(
        ident: String,
        periodeId: UUID,
    ) = Person(ident)
        .apply {
            hendelser.addAll(
                listOf(
                    søknadHendelse(ident),
                    dagpengerMeldegruppeHendelse(ident),
                    meldepliktHendelse(ident),
                    startetArbeidssøkerperiodeHendelse(ident, periodeId),
                ),
            )
            arbeidssøkerperioder.add(lagArbeidssøkerperiode(periodeId, ident, overtattBekreftelse = true))
            setStatus(vurderNyStatus())
        }

    private fun søknadHendelse(
        ident: String,
        dato: LocalDateTime = nå,
        startDato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = SøknadHendelse(ident, dato, startDato, referanseId)

    private fun dagpengerMeldegruppeHendelse(
        ident: String,
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "DAGP", true)

    private fun annenMeldegruppeHendelse(
        ident: String,
        dato: LocalDateTime = nå,
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "ARBS", true)

    private fun meldepliktHendelse(
        ident: String,
        dato: LocalDateTime = nå,
        status: Boolean = false,
    ) = MeldepliktHendelse(ident, dato, "123", dato.plusDays(1), null, status, true)

    private fun startetArbeidssøkerperiodeHendelse(
        ident: String,
        periodeId: UUID,
    ) = StartetArbeidssøkerperiodeHendelse(periodeId, ident, nå.minusDays(1))

    private fun lagArbeidssøkerperiode(
        periodeId: UUID,
        ident: String,
        overtattBekreftelse: Boolean = false,
    ) = Arbeidssøkerperiode(
        periodeId,
        ident,
        nå.minusDays(10),
        null,
        overtattBekreftelse,
    )
}
