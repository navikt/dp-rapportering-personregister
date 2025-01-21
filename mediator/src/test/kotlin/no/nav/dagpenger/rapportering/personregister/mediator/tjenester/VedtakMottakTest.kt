package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.utils.MetrikkerTestUtil.vedtakMetrikker
import no.nav.dagpenger.rapportering.personregister.modell.Status
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class VedtakMottakTest {
    private val testRapid = TestRapid()
    private val personstatusMediator = mockk<PersonstatusMediator>(relaxed = true)

    private var formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[.SSSSSS]")

    @BeforeEach
    internal fun setUp() {
        testRapid.reset()
    }

    init {
        VedtakMottak(testRapid, personstatusMediator, vedtakMetrikker)
    }

    @Test
    fun `vi kan motta vedtak om innvilgelse`() {
        val ident = "12345678901"
        val referenseId = "29501880"
        val dato = "2020-04-07 14:31:08.840468"
        val status = Status.Type.INNVILGET

        testRapid.sendTestMessage(
            vedtak_event(
                ident,
                referenseId,
                dato,
                status,
            ),
        )

        val vedtakHendelse =
            VedtakHendelse(
                ident = ident,
                referanseId = referenseId,
                dato = LocalDateTime.parse(dato, formatter),
                status = status,
            )
        verify(exactly = 1) { personstatusMediator.behandle(vedtakHendelse) }
    }

    @Test
    fun `vi kan motta vedtak om avslag`() {
        val ident = "12345678901"
        val referenseId = "29501880"
        val dato = "2020-04-07 14:31:08.840468"
        val status = Status.Type.AVSLÅTT

        testRapid.sendTestMessage(
            vedtak_event(
                ident,
                referenseId,
                dato,
                status,
            ),
        )

        val vedtakHendelse =
            VedtakHendelse(
                ident = ident,
                referanseId = referenseId,
                dato = LocalDateTime.parse(dato, formatter),
                status = status,
            )

        verify(exactly = 1) { personstatusMediator.behandle(vedtakHendelse) }
    }
}

fun vedtak_event(
    ident: String = "12345678901",
    referenseId: String = "29501880",
    dato: String,
    status: Status.Type,
): String {
    val utfallKode =
        when (status) {
            Status.Type.INNVILGET -> "JA"
            else -> "NEI"
        }

    return """
        {
          "table": "SIAMO.VEDTAK",
          "op_ts": "$dato",
          "tokens": {
            "FODSELSNR": "$ident"
          },
          "after": {
            "VEDTAK_ID": $referenseId,
            "VEDTAKTYPEKODE": "O",
            "UTFALLKODE":  "$utfallKode"
          }
        }
        """.trimIndent()
}
