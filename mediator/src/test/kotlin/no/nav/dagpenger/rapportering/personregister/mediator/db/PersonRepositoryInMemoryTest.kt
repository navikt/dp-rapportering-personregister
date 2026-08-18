package no.nav.dagpenger.rapportering.personregister.mediator.db

import io.kotest.matchers.shouldBe
import no.nav.dagpenger.rapportering.personregister.mediator.utils.UUIDv7
import no.nav.dagpenger.rapportering.personregister.modell.AnsvarligSystem
import no.nav.dagpenger.rapportering.personregister.modell.Person
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.DagpengerMeldegruppeHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.MeldepliktHendelse
import no.nav.dagpenger.rapportering.personregister.modell.hendelser.VedtakHendelse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

class PersonRepositoryInMemoryTest {
    private lateinit var personRepository: PersonRepository
    private lateinit var person: Person

    @BeforeEach
    fun setUp() {
        personRepository = PersonRepositoryInMemory()
        person = Person("12345678901")
    }

    @Test
    fun `kan lagre en person`() {
        personRepository.lagrePerson(person)
        personRepository.hentPerson(person.ident) shouldBe person
    }

    @Test
    fun `kan oppdatere en person`() {
        personRepository.lagrePerson(person)
        personRepository.hentPerson(person.ident) shouldBe person

        person.setAnsvarligSystem(AnsvarligSystem.DP)
        personRepository.oppdaterPerson(person)
        val oppdatertPerson = personRepository.hentPerson(person.ident)
        oppdatertPerson?.versjon shouldBe 2
        oppdatertPerson?.ansvarligSystem shouldBe AnsvarligSystem.DP
    }

    @Test
    fun `kan ikke finne en person som ikke er lagret`() {
        personRepository.hentPerson(person.ident) shouldBe null
    }

    @Test
    fun `kan slette fremtidige Arena hendelser`() {
        val meldepliktHendelse =
            MeldepliktHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                referanseId = "MP123456789",
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now(),
                sluttDato = null,
                statusMeldeplikt = true,
                harMeldtSeg = true,
            )
        val meldegruppeHendelse =
            DagpengerMeldegruppeHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                referanseId = "MG123456789",
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now(),
                sluttDato = null,
                meldegruppeKode = "DAGP",
                harMeldtSeg = true,
            )
        val annenPersonHendelse =
            DagpengerMeldegruppeHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = "12345678902",
                referanseId = "MG123456780",
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now(),
                sluttDato = null,
                meldegruppeKode = "DAGP",
                harMeldtSeg = true,
            )
        val ikkeArenaHendelse =
            VedtakHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now(),
                referanseId = UUIDv7.newUuid().toString(),
                utfall = true,
                behandlingskjedeId = "behandlingskjedeId",
            )

        personRepository.lagreFremtidigHendelse(meldepliktHendelse)
        personRepository.lagreFremtidigHendelse(meldegruppeHendelse)
        personRepository.lagreFremtidigHendelse(annenPersonHendelse)
        personRepository.lagreFremtidigHendelse(ikkeArenaHendelse)
        personRepository.hentAntallFremtidigeHendelser() shouldBe 4

        personRepository.slettFremtidigeArenaHendelser(person.ident)
        personRepository.hentAntallFremtidigeHendelser() shouldBe 2
    }

    @Test
    fun `kan slette fremtidige VedtakHendelser med angitt behandlingskjedeId`() {
        val behandlingskjedeId = UUID.randomUUID().toString()
        val hendelse =
            VedtakHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now().plusDays(1),
                referanseId = UUIDv7.newUuid().toString(),
                utfall = true,
                behandlingskjedeId = behandlingskjedeId,
            )

        personRepository.lagreFremtidigHendelse(hendelse)
        personRepository.hentAntallFremtidigeHendelser() shouldBe 1

        personRepository.slettFremtidigeVedtakHendelser(person.ident, behandlingskjedeId)

        personRepository.hentAntallFremtidigeHendelser() shouldBe 0
    }

    @Test
    fun `kan slette fremtidige VedtakHendelser uten behandlingskjedeId`() {
        val hendelse =
            VedtakHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now().plusDays(1),
                referanseId = UUIDv7.newUuid().toString(),
                utfall = true,
                behandlingskjedeId = null,
            )

        personRepository.lagreFremtidigHendelse(hendelse)
        personRepository.hentAntallFremtidigeHendelser() shouldBe 1

        personRepository.slettFremtidigeVedtakHendelser(person.ident, "ukjent-kjedeId")

        personRepository.hentAntallFremtidigeHendelser() shouldBe 0
    }

    @Test
    fun `beholder VedtakHendelse med annen behandlingskjedeId ved sletting`() {
        val hendelse =
            VedtakHendelse(
                korrelasjonsId = UUIDv7.newUuid(),
                ident = person.ident,
                dato = LocalDateTime.now(),
                startDato = LocalDateTime.now().plusDays(1),
                referanseId = UUIDv7.newUuid().toString(),
                utfall = true,
                behandlingskjedeId = UUID.randomUUID().toString(),
            )

        personRepository.lagreFremtidigHendelse(hendelse)
        personRepository.hentAntallFremtidigeHendelser() shouldBe 1

        personRepository.slettFremtidigeVedtakHendelser(person.ident, "ukjent-kjedeId")

        personRepository.hentAntallFremtidigeHendelser() shouldBe 1
    }
}
