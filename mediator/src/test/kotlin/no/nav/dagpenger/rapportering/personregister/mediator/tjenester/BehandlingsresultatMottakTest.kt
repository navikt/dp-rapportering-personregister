package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.BehandlingRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.behandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class BehandlingsresultatMottakTest {
    private val testRapid = TestRapid()
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val behandlingRepository = mockk<BehandlingRepository>(relaxed = true)
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        BehandlingsresultatMottak(
            testRapid,
            personRepository,
            personMediator,
            fremtidigHendelseMediator,
            behandlingsresultatMetrikker,
        )
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `skal motta behandlingsresultat og opprette hendelser`() {
        val ident = "12345678903"
        val behandlingId = UUID.randomUUID().toString()
        val søknadId = UUID.randomUUID().toString()
        val fraOgMed1 = LocalDate.now().minusDays(10)
        val tilOgMed1 = LocalDate.now()
        val fraOgMed2 = LocalDate.now().plusDays(1)

        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser), 1) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        val behandlingsresultat =
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandletHendelse": {
                "datatype": "string",
                "id": "$søknadId",
                "type": "Søknad"
              },
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed1",
                  "tilOgMed": "$tilOgMed1",
                  "harRett": true,
                  "opprinnelse": "Ny"
                },
                {
                  "fraOgMed": "$fraOgMed2",
                  "harRett": false,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent()

        testRapid.sendTestMessage(behandlingsresultat)

        verify { personRepository.slettFremtidigeVedtakHendelser(eq(ident)) }

        hendelser.size shouldBe 1
        hendelser[0].ident shouldBe ident
        hendelser[0].dato.toLocalDate() shouldBe LocalDate.now()
        hendelser[0].startDato shouldBe fraOgMed1.atStartOfDay()
        hendelser[0].sluttDato shouldBe tilOgMed1.atStartOfDay()
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        hendelser[0].utfall shouldBe true

        fremtidigeHendelser.size shouldBe 1
        fremtidigeHendelser[0].ident shouldBe ident
        fremtidigeHendelser[0].dato.toLocalDate() shouldBe LocalDate.now()
        fremtidigeHendelser[0].startDato shouldBe fraOgMed2.atStartOfDay()
        fremtidigeHendelser[0].sluttDato shouldBe null
        fremtidigeHendelser[0].referanseId shouldBe "$behandlingId-1"
        fremtidigeHendelser[0].utfall shouldBe false
    }

    @Test
    fun `skal hente søknadId fra behandletHendelse når type er Søknad`() {
        val ident = "12345678903"
        val behandlingId = UUID.randomUUID().toString()
        val søknadId = UUID.randomUUID().toString()
        val fraOgMed = LocalDate.now().minusDays(10)

        val hendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser), 1) } just runs

        val behandlingsresultat =
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandletHendelse": {
                "datatype": "string",
                "id": "$søknadId",
                "type": "Søknad"
              },
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent()

        testRapid.sendTestMessage(behandlingsresultat)

        verify { personRepository.slettFremtidigeVedtakHendelser(eq(ident)) }

        hendelser.size shouldBe 1
        hendelser[0].ident shouldBe ident
        hendelser[0].dato.toLocalDate() shouldBe LocalDate.now()
        hendelser[0].startDato shouldBe fraOgMed.atStartOfDay()
        hendelser[0].sluttDato shouldBe null
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        hendelser[0].utfall shouldBe true
    }
}
