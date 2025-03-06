#language: no
Egenskap: Person
  Brukere som søker om dagpenger og får avslag

  Scenario: Avslag etter søknad om dagpenger
    Gitt en arbeidssøker
    Når personen søker dagpenger
    Så skal personen være "IKKE_DAGPENGERBRUKER"

    Når personen får meldeplikt og DAGP-meldegruppe
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi frasier oss ansvaret for personen

  Scenario: Klage på avslag fører til medhold
    Gitt en arbeidssøker
    Når personen søker dagpenger
    Så skal personen være "IKKE_DAGPENGERBRUKER"

    Når personen får meldeplikt og DAGP-meldegruppe
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi frasier oss ansvaret for personen

    Når personen klager på vedtaket
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får medhold i klagen
    Så skal personen være "DAGPENGERBRUKER"
    Og vi beholder ansvaret for arbeidssøkerbekreftelse

  Scenario: Klage på avslag fører ikke til medhold
    Gitt en arbeidssøker
    Når personen søker dagpenger
    Så skal personen være "IKKE_DAGPENGERBRUKER"

    Når personen får meldeplikt og DAGP-meldegruppe
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi frasier oss ansvaret for personen

    Når personen klager på vedtaket
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får avslag på klagen
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi frasier oss ansvaret for personen
