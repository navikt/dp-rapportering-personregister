package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.PersonSynkroniseringHendelse
import no.nav.dagpenger.rapportering.personregister.modell.StartetArbeidssøkerperiodeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.SøknadHendelse
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RettPersonStatusUtilsTest {
    val ident = "12345678903"

    @Nested
    inner class BeregnerMeldepliktStatus {
        @Test
        fun `perosnsynkroniseringhendelse etter siste meldeplikthendelse`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(
                            meldepliktHendelse(dato = tidligere, status = false),
                            personSynkroniseringHendelse(dato = nå, referanseId = "123"),
                        ),
                    )
                }

            beregnMeldepliktStatus(person) shouldBe true
        }

        @Test
        fun `beregner meldeplikt status ved tom liste`() {
            val person = Person(ident)

            beregnMeldepliktStatus(person) shouldBe false
        }

        @Test
        fun `beregner meldeplikt status når alle false`() {
            val meldepliktHendelse = meldepliktHendelse(status = false)
            val person = Person(ident).apply { hendelser.add(meldepliktHendelse) }

            beregnMeldepliktStatus(person) shouldBe false
        }

        @Test
        fun `beregner meldeplikt status når siste er false`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(meldepliktHendelse(dato = nå, status = false), meldepliktHendelse(dato = tidligere, status = true)),
                    )
                }

            beregnMeldepliktStatus(person) shouldBe false
        }

        @Test
        fun `beregner meldeplikt status når siste er true`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(
                            meldepliktHendelse(dato = tidligere.minusDays(1), startDato = nå, status = true),
                            meldepliktHendelse(dato = nå, startDato = tidligere, status = false),
                        ),
                    )
                }

            beregnMeldepliktStatus(person) shouldBe true
        }

        @Test
        fun `beregner meldeplikt status når alle er true`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(meldepliktHendelse(dato = nå, status = true), meldepliktHendelse(dato = tidligere, status = true)),
                    )
                }

            beregnMeldepliktStatus(person) shouldBe true
        }
    }

    @Nested
    inner class BeregnerMeldegruppe {
        @Test
        fun `beregner meldegruppe status ved tom liste`() {
            val person = Person(ident)

            beregnMeldegruppeStatus(person) shouldBe null
        }

        @Test
        fun `beregner meldegruppe status når siste er ARBS`() {
            val person =
                Person(ident).apply {
                    hendelser.add(annenMeldegruppeHendelse())
                }

            beregnMeldegruppeStatus(person) shouldBe "ARBS"
        }

        @Test
        fun `beregner meldegruppe status når siste er ARBS og tidligere er DAGP`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(
                            annenMeldegruppeHendelse(
                                dato = nå,
                                referanseId = "123",
                            ),
                            dagpengerMeldegruppeHendelse(
                                dato = tidligere,
                                referanseId = "123",
                            ),
                        ),
                    )
                }

            beregnMeldegruppeStatus(person) shouldBe "ARBS"
        }

        @Test
        fun `beregner meldegruppe status når alle er DAGP`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(
                            dagpengerMeldegruppeHendelse(
                                dato = nå,
                                referanseId = "123",
                            ),
                            dagpengerMeldegruppeHendelse(
                                dato = tidligere,
                                referanseId = "456",
                            ),
                        ),
                    )
                }

            beregnMeldegruppeStatus(person) shouldBe "DAGP"
        }

        @Test
        fun `beregner meldegruppe status når alle er ARBS`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val person =
                Person(ident).apply {
                    hendelser.addAll(
                        listOf(
                            annenMeldegruppeHendelse(
                                dato = nå,
                                referanseId = "123",
                            ),
                            annenMeldegruppeHendelse(
                                dato = tidligere,
                                referanseId = "456",
                            ),
                        ),
                    )
                }

            beregnMeldegruppeStatus(person) shouldBe "ARBS"
        }
    }

    @Nested
    inner class BeregnerStatus {
        @Test
        fun `oppfyller krav ved å ha kun meldegruppe synk hendelser`() {
            val nå = LocalDateTime.now()
            val hendelse1 = annenMeldegruppeHendelse(dato = nå, referanseId = "123")
            val hendelse2 = personSynkroniseringHendelse(dato = nå.minusDays(1), referanseId = "456")

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2))
                beregnStatus(this) shouldBe Status.IKKE_DAGPENGERBRUKER
            }
        }

        @Test
        fun `har kun personsynkroniseringhendelse`() {
            val nå = LocalDateTime.now()
            val hendelse = personSynkroniseringHendelse(dato = nå)
            val søknad = SøknadHendelse(ident, nå, nå, "123")

            arbeidssøker {
                hendelser.add(hendelse)
                hendelser.add(søknad)
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `har kun personsynkronisering og DAGP hendelse`() {
            val nå = LocalDateTime.now()
            val hendelse1 = personSynkroniseringHendelse(dato = nå)
            val hendelse2 = dagpengerMeldegruppeHendelse(dato = nå, referanseId = "123")

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `har kun personsynkronisering og meldeplikt hendelse`() {
            val nå = LocalDateTime.now()
            val hendelse1 = personSynkroniseringHendelse(dato = nå)
            val hendelse2 = meldepliktHendelse(dato = nå, status = true)

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `beregn meldekort med samtidig hendelser`() {
            val nå = LocalDateTime.of(2023, 10, 1, 12, 0)
            val tidligere = LocalDateTime.of(2023, 9, 30, 12, 0)

            val hendelse1 = dagpengerMeldegruppeHendelse(dato = nå, startDato = nå, referanseId = "123")
            val hendelse2 = annenMeldegruppeHendelse(dato = tidligere, startDato = nå, referanseId = "456")

            val person =
                Person(ident)
                    .apply { hendelser.addAll(listOf(hendelse1, hendelse2)) }

            beregnMeldegruppeStatus(person) shouldBe "DAGP"
        }

        @Test
        fun `rekkefølge spiller ingen rolle`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val p = personSynkroniseringHendelse(dato = tidligere, referanseId = "123")
            val hendelse1 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)
            val hendelse2 = AnnenMeldegruppeHendelse(ident, nå, "456", nå.plusDays(1), null, "ARBS", true)
            val person =
                Person(ident).apply {
                    hendelser.addAll(listOf(hendelse1, hendelse2))
                    setStatus(Status.DAGPENGERBRUKER)
                }

            beregnStatus(person) shouldBe Status.IKKE_DAGPENGERBRUKER
            beregnStatus(person) shouldNotBeEqual person.status
        }

        @Test
        fun `riktig hendelser og riktig rekkefølge`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)
            val hendelse2 = AnnenMeldegruppeHendelse(ident, tidligere, "456", tidligere.plusDays(1), null, "ARBS", true)
            val person =
                Person(ident).apply {
                    hendelser.addAll(listOf(hendelse1, hendelse2))
                    setStatus(Status.DAGPENGERBRUKER)
                }

            beregnStatus(person) shouldNotBeEqual Status.DAGPENGERBRUKER
        }

        @Test
        fun `har kun PersonsynkroniseringHendelse og DAGP`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = PersonSynkroniseringHendelse(ident, nå, "123", nå)
            val hendelse2 = DagpengerMeldegruppeHendelse(ident, tidligere, "456", tidligere.plusDays(1), null, "DAG", true)

            arbeidssøker {
                hendelser.addAll(
                    listOf(
                        hendelse1,
                        hendelse2,
                        StartetArbeidssøkerperiodeHendelse(
                            UUID.randomUUID(),
                            "12345678903",
                            nå.minusDays(2),
                        ),
                    ),
                )

                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `ingen PersonsynkroniseringHendelse`() {
            val nå = LocalDateTime.now()
            val hendelse1 = DagpengerMeldegruppeHendelse(ident, nå, "456", nå, null, "DAGP", true)
            val hendelse2 = meldepliktHendelse(dato = nå.minusDays(1), status = true)

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `har kun PersonsynkroniseringHendelse`() {
            val nå = LocalDateTime.now()
            val hendelse = PersonSynkroniseringHendelse(ident, nå, "123", nå)

            arbeidssøker {
                hendelser.add(hendelse)
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `siste hendelse er PersonsynkroniseringHendelse`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = PersonSynkroniseringHendelse(ident, nå, "123", nå)
            val hendelse2 = AnnenMeldegruppeHendelse(ident, tidligere, "456", nå, null, "ARBS", true)
            val hendelse3 = DagpengerMeldegruppeHendelse(ident, tidligere, "456", nå, null, "DAGP", true)

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2, hendelse3))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `PersonsynkroniseringHendelse er i midten`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)
            val hendelse2 = AnnenMeldegruppeHendelse(ident, nå, "456", nå, null, "ARBS", true)
            val hendelse3 = AnnenMeldegruppeHendelse(ident, tidligere.minusDays(1), "456", tidligere.minusDays(1), null, "ARBS", true)
            val person = Person(ident).apply { hendelser.addAll(listOf(hendelse1, hendelse2, hendelse3)) }

            beregnStatus(person) shouldBe Status.IKKE_DAGPENGERBRUKER
        }

        @Test
        fun `PersonsynkroniseringHendelse er eldste`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = AnnenMeldegruppeHendelse(ident, nå, "456", nå, null, "ARBS", true)
            val hendelse2 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)
            val person = Person(ident).apply { hendelser.addAll(listOf(hendelse1, hendelse2)) }

            beregnStatus(person) shouldBe Status.IKKE_DAGPENGERBRUKER
        }

        @Test
        fun `PersonsynkroniseringHendelse er eldste men siste er 'DAGP' uten meldeplikt`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = DagpengerMeldegruppeHendelse(ident, nå, "456", nå, null, "DAGP", true)
            val hendelse2 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }

        @Test
        fun `PersonsynkroniseringHendelse er eldste men siste er 'DAGP' med meldeplikt`() {
            val nå = LocalDateTime.now()
            val tidligere = nå.minusDays(1)
            val hendelse1 = DagpengerMeldegruppeHendelse(ident, nå, "456", nå, null, "DAGP", true)
            val hendelse2 = PersonSynkroniseringHendelse(ident, tidligere, "123", tidligere)
            val hendelse3 = meldepliktHendelse(dato = tidligere.minusDays(1), status = true)

            arbeidssøker {
                hendelser.addAll(listOf(hendelse1, hendelse2, hendelse3))
                beregnStatus(this) shouldBe Status.DAGPENGERBRUKER
            }
        }
    }

    private fun meldepliktHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        startDato: LocalDateTime = dato.plusDays(1),
        status: Boolean = false,
    ) = MeldepliktHendelse(ident, dato, "123", startDato, null, status, true)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        startDato: LocalDateTime = dato.plusDays(1),
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, startDato, null, "DAGP", true)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        startDato: LocalDateTime = dato.plusDays(1),
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, startDato, null, "ARBS", true)

    private fun personSynkroniseringHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        startDato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = PersonSynkroniseringHendelse(ident, dato, referanseId, startDato)
}

private fun arbeidssøker(
    overtattBekreftelse: Boolean = false,
    block: Person.() -> Unit,
) {
    Person("1234567903")
        .apply {
            arbeidssøkerperioder.add(
                Arbeidssøkerperiode(
                    UUID.randomUUID(),
                    ident,
                    LocalDateTime.now(),
                    null,
                    overtattBekreftelse = overtattBekreftelse,
                ),
            )
        }.apply(block)
}
