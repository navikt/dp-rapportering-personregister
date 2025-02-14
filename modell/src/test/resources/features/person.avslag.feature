#language: no
Egenskap: Person
  Brukere som søker om dagpenger og får avslag

  Scenario: Person søker om dagpenger og får avslag
    Gitt en person
    Når personen registrerer seg som arbeidssøker og søker dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi skal ikke lenger ha ansvaret for arbeidssøkeren

  Scenario: Person søker om dagpenger,får avslag, klager og får medhold
    Gitt en person
    Når personen registrerer seg som arbeidssøker og søker dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi skal ikke lenger ha ansvaret for arbeidssøkeren

    Når personen klager på vedtaket
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får medhold i klagen
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal fortsatt ha ansvaret for personen

  Scenario: Person søker om dagpenger,får avslag, klager og får ikke medhold
    Gitt en person
    Når personen registrerer seg som arbeidssøker og søker dagpenger
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får avslag
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi skal ikke lenger ha ansvaret for arbeidssøkeren

    Når personen klager på vedtaket
    Så skal personen være "DAGPENGERBRUKER"
    Og vi skal ha overtatt bekreftelse for personen

    Når personen senere får avslag på klagen
    Så skal personen være "IKKE_DAGPENGERBRUKER"
    Og vi skal ikke lenger ha ansvaret for arbeidssøkeren
