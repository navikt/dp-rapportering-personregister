package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.mockk.Called
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.FremtidigHendelseMediator
import no.nav.dagpenger.rapportering.personregister.mediator.PersonMediator
import no.nav.dagpenger.rapportering.personregister.mediator.db.MeldingerRepository
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.behandlingsresultatMetrikker
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDate.now
import java.time.format.DateTimeFormatter.ISO_DATE
import java.time.format.DateTimeFormatter.ISO_LOCAL_DATE

class BehandlingsresultatMottakTest {
    private val testRapid = TestRapid()
    private val personRepository = mockk<PersonRepository>(relaxed = true)
    private val meldingerRepository = mockk<MeldingerRepository>(relaxed = true)
    private val personMediator = mockk<PersonMediator>(relaxed = true)
    private val fremtidigHendelseMediator = mockk<FremtidigHendelseMediator>(relaxed = true)

    init {
        System.setProperty("KAFKA_SCHEMA_REGISTRY", "KAFKA_SCHEMA_REGISTRY")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_USER", "KAFKA_SCHEMA_REGISTRY_USER")
        System.setProperty("KAFKA_SCHEMA_REGISTRY_PASSWORD", "KAFKA_SCHEMA_REGISTRY_PASSWORD")
        System.setProperty("KAFKA_BROKERS", "KAFKA_BROKERS")

        BehandlingsresultatMottak(
            testRapid,
            personRepository,
            personMediator,
            fremtidigHendelseMediator,
            behandlingsresultatMetrikker,
            meldingerRepository,
        )
    }

    @BeforeEach
    fun setup() {
        testRapid.reset()
    }

    @Test
    fun `onPacket behandler melding, lagrer den og inkrementerer metrikk`() {
        val metrikkCount = behandlingsresultatMetrikker.behandlingsresultatMottatt.count()
        val ident = "12345678903"
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val fraOgMed1 = now().minusDays(10)
        val tilOgMed1 = now().minusDays(1)
        val fraOgMed2 = now().plusDays(1)

        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser), 1) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        val behandlingsresultat =
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
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
        coVerify(exactly = 1) {
            meldingerRepository.lagreInnkommendeMelding(
                korrelasjonsId = any(),
                ident = ident,
                relevantMeldingsinnhold =
                    match { melding ->
                        with(defaultObjectMapper.readTree(melding)) {
                            this["ident"].asText() == ident &&
                                this["rettighetsperioder"].size() == 2 &&
                                this["rettighetsperioder"][0]["fraOgMed"].asText() == fraOgMed1.format(ISO_DATE) &&
                                this["rettighetsperioder"][0]["tilOgMed"].asText() == tilOgMed1.format(ISO_DATE) &&
                                this["rettighetsperioder"][0]["harRett"].asBoolean() &&
                                this["rettighetsperioder"][0]["opprinnelse"].asText() == "Ny" &&
                                this["rettighetsperioder"][1]["fraOgMed"].asText() == fraOgMed2.format(ISO_DATE) &&
                                !this["rettighetsperioder"][1]["harRett"].asBoolean() &&
                                this["rettighetsperioder"][1]["opprinnelse"].asText() == "Ny"
                        }
                    },
            )
        }

        hendelser.size shouldBe 1
        hendelser[0].ident shouldBe ident
        hendelser[0].dato.toLocalDate() shouldBe now()
        hendelser[0].startDato shouldBe fraOgMed1.atStartOfDay()
        hendelser[0].sluttDato shouldBe tilOgMed1.atStartOfDay()
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        hendelser[0].utfall shouldBe true

        fremtidigeHendelser.size shouldBe 1
        fremtidigeHendelser[0].ident shouldBe ident
        fremtidigeHendelser[0].dato.toLocalDate() shouldBe now()
        fremtidigeHendelser[0].startDato shouldBe fraOgMed2.atStartOfDay()
        fremtidigeHendelser[0].sluttDato shouldBe null
        fremtidigeHendelser[0].referanseId shouldBe "FREMTIDIG-START-$behandlingId-1"
        fremtidigeHendelser[0].utfall shouldBe false

        behandlingsresultatMetrikker.behandlingsresultatMottatt.count() shouldBe metrikkCount + 1
    }

    @Test
    fun `skal hente søknadId fra behandletHendelse når type er Søknad`() {
        val ident = "12345678903"
        val behandlingId = UUIDv7.newUuid().toString()
        val søknadId = UUIDv7.newUuid().toString()
        val fraOgMed = now().minusDays(10)

        val hendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser), 1) } just runs

        val behandlingsresultat =
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
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
        hendelser[0].dato.toLocalDate() shouldBe now()
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
                      "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
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

    @Test
    fun `behandlingsresultat med regelverk Ferietillegg skal ikke behandles`() {
        val metrikkCount = behandlingsresultatMetrikker.behandlingsresultatMottatt.count()

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "a9b1da30-ff3f-4484-9dad-235e620ca189",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Ferietillegg",
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

        verify { personRepository wasNot Called }
        verify { personMediator wasNot Called }
        verify { fremtidigHendelseMediator wasNot Called }
        behandlingsresultatMetrikker.behandlingsresultatMottatt.count() shouldBe metrikkCount
    }

    @Test
    fun `behandlingsresultat med regelverk som ikke er Ferietillegg skal behandles`() {
        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "a9b1da30-ff3f-4484-9dad-235e620ca189",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
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

        verify { personMediator.behandle(any<VedtakHendelse>()) }
    }

    @Test
    fun `behandlingsresultat med opprinnelse Arvet og fraOgMed i fortid skal ikke behandles`() {
        val behandlingId = "a9b1da30-ff3f-4484-9dad-235e620ca189"
        val ident = "27298194126"

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "${now().minusDays(14).format(ISO_LOCAL_DATE)}",
                  "harRett": true,
                  "opprinnelse": "Arvet"
                },
                {
                  "fraOgMed": "2020-02-02",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        verify {
            fremtidigHendelseMediator wasNot Called
        }
        verify(exactly = 1) {
            personMediator.behandle(
                withArg<VedtakHendelse> { vedtakHendelse ->
                    vedtakHendelse.ident shouldBe ident
                    vedtakHendelse.startDato shouldBe LocalDate.of(2020, 2, 2).atStartOfDay()
                    vedtakHendelse.sluttDato shouldBe null
                    vedtakHendelse.referanseId shouldBe "$behandlingId-0"
                    vedtakHendelse.utfall shouldBe true
                },
            )
        }
    }

    @Test
    fun `behandlingsresultat med opprinnelse Arvet og fraOgMed i nåtid skal ikke behandles`() {
        val behandlingId = "a9b1da30-ff3f-4484-9dad-235e620ca189"
        val ident = "27298194126"

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "${now().format(ISO_LOCAL_DATE)}",
                  "harRett": true,
                  "opprinnelse": "Arvet"
                },
                {
                  "fraOgMed": "2020-02-02",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        verify {
            fremtidigHendelseMediator wasNot Called
        }
        verify(exactly = 1) {
            personMediator.behandle(
                withArg<VedtakHendelse> { vedtakHendelse ->
                    vedtakHendelse.ident shouldBe ident
                    vedtakHendelse.startDato shouldBe LocalDate.of(2020, 2, 2).atStartOfDay()
                    vedtakHendelse.sluttDato shouldBe null
                    vedtakHendelse.referanseId shouldBe "$behandlingId-0"
                    vedtakHendelse.utfall shouldBe true
                },
            )
        }
    }

    @Test
    fun `behandlingsresultat med opprinnelse Arvet og fraOgMed i fremtid skal behandles`() {
        val behandlingId = "a9b1da30-ff3f-4484-9dad-235e620ca189"
        val ident = "27298194126"
        val fremtidigFraOgMed = now().plusDays(10)

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "${fremtidigFraOgMed.format(ISO_LOCAL_DATE)}",
                  "harRett": true,
                  "opprinnelse": "Arvet"
                },
                {
                  "fraOgMed": "2020-02-02",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        verify(exactly = 1) {
            personMediator.behandle(any<VedtakHendelse>())
        }
        verify(exactly = 1) {
            fremtidigHendelseMediator.behandle(any())
        }
        verify(exactly = 1) {
            fremtidigHendelseMediator.behandle(
                withArg<VedtakHendelse> { vedtakHendelse ->
                    vedtakHendelse.ident shouldBe ident
                    vedtakHendelse.startDato shouldBe fremtidigFraOgMed.atStartOfDay()
                    vedtakHendelse.sluttDato shouldBe null
                    vedtakHendelse.referanseId shouldBe "FREMTIDIG-START-$behandlingId-1"
                    vedtakHendelse.utfall shouldBe true
                },
            )
        }
    }

    @Test
    fun `behandlingsresultat med opprinnelse Arvet og tilOgMed i fremtid skal behandles`() {
        val behandlingId = "a9b1da30-ff3f-4484-9dad-235e620ca189"
        val ident = "27298194126"
        val fremtidigTilOgMed = now().plusDays(10)

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "2020-02-02",
                  "tilOgMed": "${fremtidigTilOgMed.format(ISO_LOCAL_DATE)}",
                  "harRett": true,
                  "opprinnelse": "Arvet"
                }
              ]
            }
            """.trimIndent(),
        )

        verify { personMediator wasNot Called }
        verify(exactly = 1) {
            fremtidigHendelseMediator.behandle(any())
        }
        verify(exactly = 1) {
            fremtidigHendelseMediator.behandle(
                withArg<VedtakHendelse> { vedtakHendelse ->
                    vedtakHendelse.ident shouldBe ident
                    vedtakHendelse.startDato shouldBe LocalDate.of(2020, 2, 2).atStartOfDay()
                    vedtakHendelse.sluttDato shouldBe fremtidigTilOgMed.atStartOfDay()
                    vedtakHendelse.referanseId shouldBe "FREMTIDIG-STANS-$behandlingId-0"
                    vedtakHendelse.utfall shouldBe true
                },
            )
        }
    }

    @Test
    fun `behandlingsresultat med opprinnelse Arvet og tilOgMed i nåtid skal behandles som fremtidig hendelse`() {
        val behandlingId = "a9b1da30-ff3f-4484-9dad-235e620ca189"
        val ident = "27298194126"

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": {
                "datatype": "string",
                "id": "7117556b-108f-48a9-ba3a-2880604a8fd2",
                "type": "Søknad"
              },
              "regelverk": "Annet regelverk",
              "basertPå": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [ ],
              "rettighetsperioder": [
                {
                  "fraOgMed": "2020-02-02",
                  "tilOgMed": "${now().format(ISO_LOCAL_DATE)}",
                  "harRett": true,
                  "opprinnelse": "Arvet"
                }
              ]
            }
            """.trimIndent(),
        )

        verify {
            personMediator wasNot Called
        }
        verify(exactly = 1) {
            fremtidigHendelseMediator.behandle(
                withArg<VedtakHendelse> { vedtakHendelse ->
                    vedtakHendelse.ident shouldBe ident
                    vedtakHendelse.startDato shouldBe LocalDate.of(2020, 2, 2).atStartOfDay()
                    vedtakHendelse.sluttDato shouldBe now().atStartOfDay()
                    vedtakHendelse.referanseId shouldBe "FREMTIDIG-STANS-$behandlingId-0"
                    vedtakHendelse.utfall shouldBe true
                },
            )
        }
    }

    @Test
    fun `rettighetsperiode med fraOgMed i fortid og tilOgMed i fremtid skal behandles både nå og som fremtidig hendelse`() {
        val behandlingId = UUIDv7.newUuid().toString()
        val ident = "12345678903"
        val fraOgMed = now().minusDays(10)
        val tilOgMed = now().plusDays(30)
        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser)) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": { "datatype": "string", "id": "abc", "type": "Søknad" },
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed",
                  "tilOgMed": "$tilOgMed",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        hendelser.size shouldBe 1
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        fremtidigeHendelser.size shouldBe 1
        fremtidigeHendelser[0].referanseId shouldBe "FREMTIDIG-STANS-$behandlingId-0"
        fremtidigeHendelser[0].startDato shouldBe fraOgMed.atStartOfDay()
        fremtidigeHendelser[0].sluttDato shouldBe tilOgMed.atStartOfDay()
    }

    @Test
    fun `rettighetsperiode med både fraOgMed og tilOgMed i fremtid skal gi to fremtidige hendelser`() {
        val behandlingId = UUIDv7.newUuid().toString()
        val ident = "12345678903"
        val fraOgMed = now().plusDays(5)
        val tilOgMed = now().plusDays(30)
        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser)) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": { "datatype": "string", "id": "abc", "type": "Søknad" },
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed",
                  "tilOgMed": "$tilOgMed",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        hendelser.size shouldBe 0
        fremtidigeHendelser.size shouldBe 2
        fremtidigeHendelser.any { it.referanseId == "FREMTIDIG-START-$behandlingId-0" } shouldBe true
        fremtidigeHendelser.any { it.referanseId == "FREMTIDIG-STANS-$behandlingId-0" } shouldBe true
    }

    @Test
    fun `rettighetsperiode med både fraOgMed og tilOgMed i nåtid skal både behandles nå og gi fremtidige hendelse`() {
        val behandlingId = UUIDv7.newUuid().toString()
        val ident = "12345678903"
        val fraOgMed = now()
        val tilOgMed = now()
        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser)) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": { "datatype": "string", "id": "abc", "type": "Søknad" },
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed",
                  "tilOgMed": "$tilOgMed",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        hendelser.size shouldBe 1
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        fremtidigeHendelser.size shouldBe 1
        fremtidigeHendelser[0].referanseId shouldBe "FREMTIDIG-STANS-$behandlingId-0"
    }

    @Test
    fun `rettighetsperiode med både fraOgMed og tilOgMed i fortid skal behandles nå og ikke gi fremtidige hendelser`() {
        val behandlingId = UUIDv7.newUuid().toString()
        val ident = "12345678903"
        val fraOgMed = now().minusDays(30)
        val tilOgMed = now().minusDays(5)
        val hendelser = mutableListOf<VedtakHendelse>()
        val fremtidigeHendelser = mutableListOf<VedtakHendelse>()
        every { personMediator.behandle(capture(hendelser)) } just runs
        every { fremtidigHendelseMediator.behandle(capture(fremtidigeHendelser)) } just runs

        testRapid.sendTestMessage(
            """
            {
              "@event_name": "behandlingsresultat",
              "behandlingId": "$behandlingId",
              "behandlingskjedeId": "7117556b-108f-48a9-ba3a-2880604a8fd3",
              "behandletHendelse": { "datatype": "string", "id": "abc", "type": "Søknad" },
              "automatisk": true,
              "ident": "$ident",
              "opplysninger": [],
              "rettighetsperioder": [
                {
                  "fraOgMed": "$fraOgMed",
                  "tilOgMed": "$tilOgMed",
                  "harRett": true,
                  "opprinnelse": "Ny"
                }
              ]
            }
            """.trimIndent(),
        )

        hendelser.size shouldBe 1
        hendelser[0].referanseId shouldBe "$behandlingId-0"
        fremtidigeHendelser.size shouldBe 0
    }
}
