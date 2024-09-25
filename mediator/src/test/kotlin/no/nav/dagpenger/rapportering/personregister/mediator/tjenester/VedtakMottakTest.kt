package no.nav.dagpenger.rapportering.personregister.mediator.tjenester

import com.github.navikt.tbd_libs.rapids_and_rivers.test_support.TestRapid
import io.mockk.mockk
import io.mockk.verify
import no.nav.dagpenger.rapportering.personregister.mediator.PersonstatusMediator
import no.nav.dagpenger.rapportering.personregister.mediator.hendelser.VedtakHendelse
import no.nav.dagpenger.rapportering.personregister.mediator.melding.Vedtakstype
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
        VedtakMottak(testRapid, personstatusMediator)
    }

    @Test
    fun `vi kan motta vedtak om innvilgelse`() {
        val fnr = "12345678901"
        val vedtakId = "29501880"
        val sakId = "123"
        val fattet = LocalDateTime.parse("2020-04-07 14:31:08.840468", formatter)
        val fraDato = LocalDateTime.parse("2018-03-05 00:00:00", formatter)
        val vedtakstype = Vedtakstype.INNVILGET

        testRapid.sendTestMessage(vedtak_event(fnr, vedtakId, sakId, vedtakstype))

        val vedtakHendelse = VedtakHendelse(fnr, vedtakId, sakId, fattet, fraDato, null, vedtakstype)
        verify(exactly = 1) { personstatusMediator.behandle(vedtakHendelse) }
    }

    @Test
    fun `vi kan motta vedtak om avslag`() {
        val fnr = "12345678901"
        val vedtakId = "29501880"
        val sakId = "123"
        val fattet = LocalDateTime.parse("2020-04-07 14:31:08.840468", formatter)
        val fraDato = LocalDateTime.parse("2018-03-05 00:00:00", formatter)
        val vedtakstype = Vedtakstype.AVSLÅTT

        testRapid.sendTestMessage(vedtak_event(fnr, vedtakId, sakId, vedtakstype))

        val vedtakHendelse = VedtakHendelse(fnr, vedtakId, sakId, fattet, fraDato, null, vedtakstype)
        verify(exactly = 1) { personstatusMediator.behandle(vedtakHendelse) }
    }
}

fun vedtak_event(
    fnr: String,
    vedtakId: String,
    sakId: String,
    vedtakstype: Vedtakstype,
): String {
    val utfallKode =
        when (vedtakstype) {
            Vedtakstype.INNVILGET -> "JA"
            else -> "NEI"
        }

    println("Utfallkode: $utfallKode")
    return """
        {
          "table": "SIAMO.VEDTAK",
          "op_type": "I",
          "op_ts": "2020-04-07 14:31:08.840468",
          "current_ts": "2020-04-07T14:53:03.656001",
          "pos": "00000000000000013022",
          "tokens": {
            "FODSELSNR": "$fnr"
          },
          "after": {
            "VEDTAK_ID": $vedtakId,
            "SAK_ID": $sakId,
            "VEDTAKSTATUSKODE": "IVERK",
            "VEDTAKTYPEKODE": "O",
            "UTFALLKODE":  "$utfallKode",
            "RETTIGHETKODE": "DAGO",
            "PERSON_ID": 4124685,
            "FRA_DATO": "2018-03-05 00:00:00",
            "TIL_DATO": null
          }
        }
        """.trimIndent()
}
