package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import no.nav.dagpenger.rapportering.personregister.mediator.db.PersonRepository
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.UUID

class HentArbeidssøkerstatusJobTest {
    private val rapid = TestRapid()
    private val personRepository = mockk<PersonRepository>()
    private val hentArbeidssøkerstatusJob = HentArbeidssøkerstatusJob(rapid, personRepository)

    @Test
    fun `beOmArbeidssøkerstatus sender melding på riktig format`() {
        val hendelseId = UUID.randomUUID()
        hentArbeidssøkerstatusJob.beOmArbeidssøkerstatus("12345678910", "søknadsId", hendelseId)

        with(rapid.inspektør) {
            size shouldBe 1
            field(0, "@event_name").asText() shouldBe "behov"
            field(0, "@behov")[0].asText() shouldBe "RegistrertSomArbeidssøker"
            field(0, "ident").asText() shouldBe "12345678910"
            field(0, "søknadId").asText() shouldBe "søknadsId"
            field(0, "behandlingId").asText() shouldBe hendelseId.toString()
            field(0, "gjelderDato").asText() shouldBe LocalDate.now().toString()
        }
    }
}
