package no.nav.dagpenger.rapportering.personregister.mediator.hendelser

data class TestTopicHendelse(
    val periodeId: String,
    val bekreftelsesLøsning: String,
    val start: Start,
)

data class Start(
    val intervalMS: Long,
    val graceMS: Long,
)
