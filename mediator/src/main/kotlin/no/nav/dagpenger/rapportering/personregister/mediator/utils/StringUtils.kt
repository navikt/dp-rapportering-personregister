package no.nav.dagpenger.rapportering.personregister.mediator.utils

private val ident11SifferRegex = Regex("[0-9]{11}")

fun String.validerIdent() = require(this.matches(ident11SifferRegex)) { "Person-ident må ha 11 sifre" }
