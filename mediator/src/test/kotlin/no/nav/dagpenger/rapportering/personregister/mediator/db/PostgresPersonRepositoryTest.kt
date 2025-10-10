package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.date.after
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.dagpenger.rapportering.personregister.mediator.Configuration.defaultObjectMapper
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.dataSource
import no.nav.dagpenger.rapportering.personregister.mediator.db.Postgres.withMigratedDb
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.actionTimer
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Arbeidssøkerperiode
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.Status
import no.nav.dagpenger.rapportering.personregister.modell.Status.DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.Status.IKKE_DAGPENGERBRUKER
import no.nav.dagpenger.rapportering.personregister.modell.TemporalCollection
import no.nav.dagpenger.rapportering.personregister.modell.VedtakType
import no.nav.dagpenger.rapportering.personregister.modell.gjeldende
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.AnnenMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.Hendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.SøknadHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PostgresPersonRepositoryTest {
    private val personRepository = PostgresPersonRepository(dataSource, actionTimer)
    private val ident = "12345678901"
    private val nå = LocalDateTime.now()

    @Test
    @Disabled
    fun `kan lagre og hente komplett person`() =
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            personRepository.hentPerson(ident)?.apply {
                ident shouldBe person.ident
                hendelser shouldBe person.hendelser
                status shouldBe person.status
                arbeidssøkerperioder shouldBe person.arbeidssøkerperioder
            }
        }

    @Test
    @Disabled
    fun `kan oppdatere person`() =
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            val nyHendelse = meldegruppeHendelse()
            person.hendelser.add(nyHendelse)
            person.setMeldeplikt(true)
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
                hendelser shouldBe listOf(person.hendelser.first(), nyHendelse)
            }
        }

    @Test
    fun `oppdatering av ikke-eksisterende person kaster IllegalStateException`() =
        withMigratedDb {
            shouldThrow<IllegalStateException> { personRepository.oppdaterPerson(Person(ident)) }
        }

    @Test
    fun `kan ikke hente ikke-eksisterende person`() =
        withMigratedDb {
            personRepository.hentPerson(ident) shouldBe null
        }

    @Test
    fun `kan sjekke om person finnes`() =
        withMigratedDb {
            val person = Person(ident)
            personRepository.finnesPerson(ident) shouldBe false
            personRepository.lagrePerson(person)
            personRepository.finnesPerson(ident) shouldBe true
        }

    @Test
    fun `kan hente antall personer og hendelser`() =
        withMigratedDb {
            val person = testPerson(hendelser = mutableListOf(søknadHendelse()))
            personRepository.lagrePerson(person)
            personRepository.hentAntallPersoner() shouldBe 1
            personRepository.hentAntallHendelser() shouldBe person.hendelser.size
        }

    @Test
    fun `kan lagre, hente og slette fremtidige hendelser`() =
        withMigratedDb {
            val person = Person(ident = ident)
            val meldepliktHendelse = meldepliktHendelse(ident)
            val meldegruppeHendelse = meldegruppeHendelse()

            personRepository.lagrePerson(person)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse)

            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 2
                any { it.javaClass == MeldepliktHendelse::class.java } shouldBe true
                any { it.javaClass == DagpengerMeldegruppeHendelse::class.java } shouldBe true
            }

            personRepository.slettFremtidigHendelse(meldepliktHendelse.referanseId)
            personRepository.slettFremtidigHendelse(meldegruppeHendelse.referanseId)

            personRepository.hentHendelserSomSkalAktiveres().size shouldBe 0
        }

    @Test
    fun `kan slette fremtidige Arena hendelser`() =
        withMigratedDb {
            val ident2 = "12345678902"

            val person1 = Person(ident = ident)
            val person2 = Person(ident = ident2)

            val meldepliktHendelse = meldepliktHendelse(ident, "MP123456789")
            val meldegruppeHendelse = meldegruppeHendelse("MG123456789")
            val ikkeArenaHendelse =
                VedtakHendelse(
                    ident = ident,
                    dato = LocalDateTime.now(),
                    startDato = LocalDateTime.now(),
                    referanseId = UUID.randomUUID().toString(),
                    utfall = true,
                )

            personRepository.lagrePerson(person1)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse)
            personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
            personRepository.lagreFremtidigHendelse(ikkeArenaHendelse)

            val meldepliktHendelse2 = meldepliktHendelse(ident2)
            personRepository.lagrePerson(person2)
            personRepository.lagreFremtidigHendelse(meldepliktHendelse2)

            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 4
                any { it.javaClass == MeldepliktHendelse::class.java } shouldBe true
                any { it.javaClass == DagpengerMeldegruppeHendelse::class.java } shouldBe true
                any { it.javaClass == VedtakHendelse::class.java } shouldBe true
            }

            personRepository.slettFremtidigeArenaHendelser(ident)

            // Sjekker at vi har fortsatt ikke-arena hendelse og annen persons hendelse
            with(personRepository.hentHendelserSomSkalAktiveres()) {
                size shouldBe 2
                any { it.javaClass == VedtakHendelse::class.java } shouldBe true
                any { it.javaClass == MeldepliktHendelse::class.java } shouldBe true
                any { it.ident == ident } shouldBe true
                any { it.ident == ident2 } shouldBe true
            }
        }

    @Test
    fun `hendelser med samme referanse overskriver hverandre`() =
        withMigratedDb {
            val person = Person(ident)
            val referanseId = UUID.randomUUID().toString()
            val førsteHendelse = meldegruppeHendelse(referanseId)
            val nyHendelse = meldepliktHendelse(ident, referanseId)

            personRepository.lagrePerson(person)
            personRepository.lagreFremtidigHendelse(førsteHendelse)
            personRepository.hentHendelserSomSkalAktiveres().first().javaClass shouldBe førsteHendelse.javaClass

            personRepository.lagreFremtidigHendelse(nyHendelse)
            personRepository.hentHendelserSomSkalAktiveres().first().javaClass shouldBe nyHendelse.javaClass
        }

    @Test
    fun `hendelser henter ut riktig fristBrutt-verdi`() {
        withMigratedDb {
            val person =
                Person(ident).apply {
                    this.hendelser.add(meldegruppeHendelse(harMeldtSeg = false))
                    this.hendelser.add(meldegruppeHendelse(meldegruppeKode = "ARBS", harMeldtSeg = true))
                }
            personRepository.lagrePerson(person)

            with(personRepository.hentPerson(ident)!!) {
                hendelser.size shouldBe 2
                (hendelser.first() as DagpengerMeldegruppeHendelse).harMeldtSeg shouldBe false
                (hendelser.last() as AnnenMeldegruppeHendelse).harMeldtSeg shouldBe true
            }
        }
    }

    @Test
    fun `kan håndtere lagrede hendelser uten fristBrutt`() =
        withMigratedDb {
            val person = Person(ident)
            personRepository.lagrePerson(person)
            val hendelse = meldegruppeHendelse(meldegruppeKode = "ARBS")

            using(sessionOf(dataSource)) { session ->
                val personId =
                    session.run(
                        queryOf("select id from person where ident = ?", ident).map { it.int("id") }.asSingle,
                    )
                session.transaction { tx ->
                    tx.run(
                        queryOf(
                            """
                INSERT INTO hendelse (person_id, dato, start_dato, slutt_dato, kilde,referanse_id, type, extra) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """,
                            personId,
                            hendelse.dato,
                            LocalDateTime.now(),
                            LocalDateTime.now(),
                            hendelse.kilde.name,
                            hendelse.referanseId,
                            hendelse::class.simpleName,
                            defaultObjectMapper.writeValueAsString(
                                MeldegruppeExtra(
                                    meldegruppeKode = "ARBS",
                                ),
                            ),
                        ).asUpdate,
                    )
                }
            }

            val personMedHendelse = personRepository.hentPerson(ident)

            with(personMedHendelse!!) {
                hendelser.size shouldBe 1
                hendelser.first().javaClass shouldBe AnnenMeldegruppeHendelse::class.java
                (hendelser.first() as AnnenMeldegruppeHendelse).harMeldtSeg shouldBe true
            }
        }

    @Test
    fun `lagrer ikke ny status dersom den er samme som nåværende status`() {
        withMigratedDb {
            val person = testPerson(status = IKKE_DAGPENGERBRUKER)
            personRepository.lagrePerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    status shouldBe IKKE_DAGPENGERBRUKER
                    statusHistorikk.getAll() shouldHaveSize 1
                }

            person.setStatus(DAGPENGERBRUKER)
            personRepository.oppdaterPerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    status shouldBe DAGPENGERBRUKER
                    statusHistorikk.getAll() shouldHaveSize 2
                }

            person.setStatus(DAGPENGERBRUKER)
            personRepository.oppdaterPerson(person.copy(versjon = person.versjon + 1))

            personRepository
                .hentPerson(ident)
                ?.apply {
                    status shouldBe DAGPENGERBRUKER
                    statusHistorikk.getAll() shouldHaveSize 2
                }
        }
    }

    @Test
    fun `kan oppdatere arbeidssøkerperiode med avsluttet-dato`() =
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            val nyPeriode = person.arbeidssøkerperioder.gjeldende!!.copy(avsluttet = nå)
            person.arbeidssøkerperioder.add(nyPeriode)
            personRepository.oppdaterPerson(person)

            personRepository.hentPerson(ident)?.apply {
                arbeidssøkerperioder shouldHaveSize 1
                arbeidssøkerperioder.first().avsluttet shouldNotBe null
            }
        }

    @Test
    fun `lagre arbeidssøkerperiode og oppdatere person oppdaterer ikke sist_endret timestamp`() {
        withMigratedDb {
            val person =
                testPerson(
                    hendelser = mutableListOf(søknadHendelse()),
                    arbeidssøkerperiode = mutableListOf(arbeidssøkerperiode()),
                )
            personRepository.lagrePerson(person)

            val originalTimestamp =
                using(sessionOf(dataSource)) { session ->
                    session.run(
                        queryOf(
                            "SELECT sist_endret FROM arbeidssoker WHERE periode_id = ?",
                            person.arbeidssøkerperioder.first().periodeId,
                        ).map { it.localDateTime("sist_endret") }.asSingle,
                    )
                }

            val nyHendelse = meldegruppeHendelse()
            person.hendelser.add(nyHendelse)
            personRepository.oppdaterPerson(person)

            val oppdatertTimestamp =
                using(sessionOf(dataSource)) { session ->
                    session.run(
                        queryOf(
                            "SELECT sist_endret FROM arbeidssoker WHERE periode_id = ?",
                            person.arbeidssøkerperioder.first().periodeId,
                        ).map { it.localDateTime("sist_endret") }.asSingle,
                    )
                }

            originalTimestamp shouldBe oppdatertTimestamp

            person.arbeidssøkerperioder.add(person.arbeidssøkerperioder.gjeldende!!.copy(avsluttet = nå))
            personRepository.oppdaterPerson(person.copy(versjon = person.versjon + 1))

            val avsluttetTimestamp =
                using(sessionOf(dataSource)) { session ->
                    session.run(
                        queryOf(
                            "SELECT sist_endret FROM arbeidssoker WHERE periode_id = ?",
                            person.arbeidssøkerperioder.first().periodeId,
                        ).map { it.localDateTime("sist_endret") }.asSingle,
                    )
                }
            avsluttetTimestamp shouldBe after(originalTimestamp!!)
        }
    }

    @Test
    fun `kan oppdatere persons ansvarlig system`() {
        withMigratedDb {
            val person = testPerson()
            person.setAnsvarligSystem(AnsvarligSystem.ARENA)
            personRepository.lagrePerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    ansvarligSystem shouldBe AnsvarligSystem.ARENA
                }

            person.setAnsvarligSystem(AnsvarligSystem.DP)
            personRepository.oppdaterPerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    ansvarligSystem shouldBe AnsvarligSystem.DP
                }
        }
    }

    @Test
    fun `kan oppdatere persons vedtak`() {
        withMigratedDb {
            val person = testPerson()
            personRepository.lagrePerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    vedtak shouldBe VedtakType.INGEN
                }

            person.setVedtak(VedtakType.INNVILGET)
            personRepository.oppdaterPerson(person)

            personRepository
                .hentPerson(ident)
                ?.apply {
                    vedtak shouldBe VedtakType.INNVILGET
                }
        }
    }

    @Test
    fun `kan hente personId ved bruk av ident og ident ved bruk av personId`() {
        withMigratedDb {
            val person = testPerson()
            personRepository.lagrePerson(person)

            val personId = personRepository.hentPersonId(ident)
            personId shouldBe 1L

            personRepository.hentIdent(1L) shouldBe ident
        }
    }

    private fun testPerson(
        hendelser: MutableList<Hendelse> = mutableListOf(),
        arbeidssøkerperiode: MutableList<Arbeidssøkerperiode> = mutableListOf(),
        status: Status = IKKE_DAGPENGERBRUKER,
    ) = Person(
        ident = ident,
        statusHistorikk = statusHistorikk(mapOf(nå to status)),
        arbeidssøkerperioder = arbeidssøkerperiode,
    ).apply { this.hendelser.addAll(hendelser) }

    private fun søknadHendelse() =
        SøknadHendelse(
            ident = "12345678901",
            referanseId = UUID.randomUUID().toString(),
            dato = LocalDateTime.now(),
            startDato = LocalDateTime.now(),
        )

    private fun arbeidssøkerperiode() =
        Arbeidssøkerperiode(
            ident = "12345678901",
            periodeId = UUID.randomUUID(),
            startet = LocalDateTime.now(),
            avsluttet = null,
            overtattBekreftelse = false,
        )

    private fun statusHistorikk(historikk: Map<LocalDateTime, Status>) =
        TemporalCollection<Status>().apply {
            historikk.forEach { (dato, status) -> put(dato, status) }
        }

    private fun meldegruppeHendelse(
        referanseId: String = UUID.randomUUID().toString(),
        meldegruppeKode: String = "DAGP",
        harMeldtSeg: Boolean = false,
    ) = if (meldegruppeKode == "DAGP") {
        DagpengerMeldegruppeHendelse(
            ident = "12345678901",
            referanseId = referanseId,
            dato = LocalDateTime.now(),
            startDato = LocalDateTime.now(),
            sluttDato = null,
            meldegruppeKode = meldegruppeKode,
            harMeldtSeg = harMeldtSeg,
        )
    } else {
        AnnenMeldegruppeHendelse(
            ident = "12345678901",
            referanseId = referanseId,
            dato = LocalDateTime.now(),
            startDato = LocalDateTime.now(),
            sluttDato = null,
            meldegruppeKode = meldegruppeKode,
            harMeldtSeg = harMeldtSeg,
        )
    }

    private fun meldepliktHendelse(
        ident: String,
        referanseId: String = UUID.randomUUID().toString(),
        harMeldtSeg: Boolean = false,
    ) = MeldepliktHendelse(
        ident = ident,
        referanseId = referanseId,
        dato = LocalDateTime.now(),
        startDato = LocalDateTime.now(),
        sluttDato = null,
        statusMeldeplikt = true,
        harMeldtSeg = harMeldtSeg,
    )
}
