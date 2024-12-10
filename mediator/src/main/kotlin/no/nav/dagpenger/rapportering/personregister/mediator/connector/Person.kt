package no.nav.dagpenger.rapportering.personregister.mediator.connector

import java.time.LocalDate

interface PersonInfomasjon {
    val forNavn: String
    val mellomNavn: String
    val etterNavn: String
    val fødselsDato: LocalDate
    val ident: String
}

data class Person(
    override val forNavn: String = "",
    override val mellomNavn: String = "",
    override val etterNavn: String = "",
    override val fødselsDato: LocalDate,
    override val ident: String,
) : PersonInfomasjon
