#language: no
Egenskap: Person
  Brukere som registrerer seg som arbeidssøker og søker dagpenger

  Scenario: Bruker søker om dagpenger, får innvilget og eventuelt stanset
    Gitt en person
    Når personen registrerer seg som arbeidssøker og søker dagpenger
    Så skal status være "SØKT"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får innvilget dagpenger
    Så skal status være "INNVILGET"
    Og vi skal fortsatt ha ansvaret for personen

    Når vedtaket til personen blir stanset
    Så skal status være "STANSET"
    Og vi skal ikke lenger ha ansvaret for personen

