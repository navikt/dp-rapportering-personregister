package no.nav.dagpenger.rapportering.personregister.mediator.jobs

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.modell.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class RettPersonStatusUtilsTest {
    val ident = "12345678903"

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
                hendelser.addAll(listOf(meldepliktHendelse(dato = nå, status = false), meldepliktHendelse(dato = tidligere, status = true)))
            }

        beregnMeldepliktStatus(person) shouldBe false
    }

    @Test
    fun `beregner meldeplikt status når siste er true`() {
        val nå = LocalDateTime.now()
        val tidligere = nå.minusDays(1)
        val person =
            Person(ident).apply {
                hendelser.addAll(listOf(meldepliktHendelse(dato = nå, status = true), meldepliktHendelse(dato = tidligere, status = false)))
            }

        beregnMeldepliktStatus(person) shouldBe true
    }

    @Test
    fun `beregner meldeplikt status når alle er true`() {
        val nå = LocalDateTime.now()
        val tidligere = nå.minusDays(1)
        val person =
            Person(ident).apply {
                hendelser.addAll(listOf(meldepliktHendelse(dato = nå, status = true), meldepliktHendelse(dato = tidligere, status = true)))
            }

        beregnMeldepliktStatus(person) shouldBe true
    }

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

    // Skriv ekstensive tester for rettPersonStatus
    @Test
    fun `rettPersonStatus oppdaterer meldeplikt og meldegruppe status`() {
        val person = Person(ident)
        val sisteArbeidssøkerperiode = null

        rettPersonStatus(person, sisteArbeidssøkerperiode)

        person.meldeplikt shouldBe false
        person.meldegruppe shouldBe null
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `rettPersonStatus oppdaterer meldeplikt og meldegruppe status med gyldig arbeidssøkerperiode`() {
        val person =
            Person(ident).apply {
                hendelser.add(meldepliktHendelse(status = true))
                hendelser.add(dagpengerMeldegruppeHendelse())
            }
        val sisteArbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = LocalDateTime.now().minusDays(10),
                avsluttet = null,
                overtattBekreftelse = false,
            )

        rettPersonStatus(person, sisteArbeidssøkerperiode)

        person.meldeplikt shouldBe true
        person.meldegruppe shouldBe "DAGP"
        person.status shouldBe Status.DAGPENGERBRUKER
    }

    @Test
    fun `rettPersonStatus oppdaterer status til IKKE_DAGPENGERBRUKER når meldeplikt er false`() {
        val person =
            Person(ident).apply {
                hendelser.add(meldepliktHendelse(status = false))
                hendelser.add(dagpengerMeldegruppeHendelse())
            }
        val sisteArbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = LocalDateTime.now().minusDays(10),
                avsluttet = null,
                overtattBekreftelse = false,
            )

        rettPersonStatus(person, sisteArbeidssøkerperiode)

        person.meldeplikt shouldBe false
        person.meldegruppe shouldBe "DAGP"
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `rettPersonStatus oppdaterer status til IKKE_DAGPENGERBRUKER når meldegruppe ikke er DAPG`() {
        val person =
            Person(ident).apply {
                hendelser.add(meldepliktHendelse(status = true))
            }
        val sisteArbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = LocalDateTime.now().minusDays(10),
                avsluttet = null,
                overtattBekreftelse = false,
            )

        rettPersonStatus(person, sisteArbeidssøkerperiode)

        person.meldeplikt shouldBe true
        person.meldegruppe shouldBe null
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
    }

    @Test
    fun `rettPersonStatus oppdaterer status til IKKE_DAGPENGERBRUKER når arbeidssøkerperiode er avsluttet`() {
        val person =
            Person(ident).apply {
                hendelser.add(meldepliktHendelse(status = true))
                hendelser.add(dagpengerMeldegruppeHendelse())
            }
        val sisteArbeidssøkerperiode =
            Arbeidssøkerperiode(
                periodeId = UUID.randomUUID(),
                ident = ident,
                startet = LocalDateTime.now().minusDays(10),
                avsluttet = LocalDateTime.now(),
                overtattBekreftelse = false,
            )

        rettPersonStatus(person, sisteArbeidssøkerperiode)

        person.meldeplikt shouldBe true
        person.meldegruppe shouldBe "DAGP"
        person.status shouldBe Status.IKKE_DAGPENGERBRUKER
    }

    private fun meldepliktHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        status: Boolean = false,
    ) = MeldepliktHendelse(ident, dato, "123", dato.plusDays(1), null, status, true)

    private fun dagpengerMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = DagpengerMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "DAGP", true)

    private fun annenMeldegruppeHendelse(
        dato: LocalDateTime = LocalDateTime.now(),
        referanseId: String = "123",
    ) = AnnenMeldegruppeHendelse(ident, dato, referanseId, dato.plusDays(1), null, "ARBS", true)
}
