package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.behandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BehandlingsresultatMottakTest {
    private val testRapid = TestRapid()
    private val personRepository = mockk<PersonRepository>(relaxed = true)
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
    fun `onPacket behandler melding og inkrementerer metrikk`() {
        val metrikkCount = behandlingsresultatMetrikker.behandlingsresultatMottatt.count()
        val ident = "12345678903"
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
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

        behandlingsresultatMetrikker.behandlingsresultatMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `skal hente søknadId fra behandletHendelse når type er Søknad`() {
        val ident = "12345678903"
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
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

    @Test
    fun `onPacket kaster exception og inkrementerer metrikk hvis behandling av melding feiler`() {
        val metrikkCount = behandlingsresultatMetrikker.behandlingsresultatFeilet.count()
        every { personMediator.behandle(any<VedtakHendelse>()) } throws RuntimeException("kaboom")

        val exception =
            shouldThrow<RuntimeException> {
                testRapid.sendTestMessage(
                    """
                    {
                      "@event_name": "behandlingsresultat",
                      "behandlingId": "a9b1da30-ff3f-4484-9dad-235e620ca189",
                      "behandletHendelse": {
                        "datatype": "string",
                        "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                        "type": "Søknad"
                      },
                      "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
                      "automatisk": true,
                      "ident": "27298194126",
                      "opplysninger": [ ],
                      "rettighetsperioder": [
                        {
                          "fraOgMed": "2020-01-01",
                          "harRett": true,
                          "opprinnelse": "Ny"
                        }
                      ]
                    }
                    """.trimIndent(),
                )
            }

        exception.message shouldBe "kaboom"
        behandlingsresultatMetrikker.behandlingsresultatFeilet.count() shouldBe metrikkCount + 1
    }
}
