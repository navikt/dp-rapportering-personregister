package no.nav.dagpenger.rapportering.personregister.kafka

import io.confluent.kafka.streams.serdes.avro.SpecificAvroDeserializer
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerializer
import no.nav.paw.arbeidssokerregisteret.api.v1.Periode
import no.nav.paw.bekreftelse.paavegneav.v1.PaaVegneAv

class PaaVegneAvAvroSerializer : SpecificAvroSerializer<PaaVegneAv>()

class PaaVegneAvAvroDeserializer : SpecificAvroDeserializer<PaaVegneAv>()

class PeriodeAvroDeserializer : SpecificAvroDeserializer<Periode>()
