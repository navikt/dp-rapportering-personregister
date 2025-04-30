#language: no
Egenskap: Person
  Brukere som registrerer seg som arbeidssøker og søker dagpenger

  Scenario: Stans etter innvilgelse av dagpenger
    Gitt en arbeidssøker
    Når personen søker dagpenger
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Når personen får meldeplikt og DAGP-meldegruppe
    Så skal personen være "DAGPENGERBRUKER"
    Og vi har overtatt bekreftelsen for personen

    Når personen får innvilget dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi beholder ansvaret for arbeidssøkerbekreftelse

    Når vedtaket til personen blir stanset
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi frasier oss ansvaret for personen

