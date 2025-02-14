#language: no
Egenskap: Person
  Brukere som registrerer seg som arbeidssøker og søker dagpenger

  Scenario: Person søker om dagpenger, får innvilget og eventuelt stanset
    Gitt en person
    Når personen registrerer seg som arbeidssøker og søker dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får innvilget dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal fortsatt ha ansvaret for personen

    Når vedtaket til personen blir stanset
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi skal ikke lenger ha ansvaret for personen

