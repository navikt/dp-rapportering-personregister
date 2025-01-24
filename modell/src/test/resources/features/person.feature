#language: no
Egenskap: Person
  Brukere som ikke er registrert i system men søker om dagpenger eller får dagpengervedtak skal få oppdatert status

  Scenariomal: Søknad gir status "SØKT"
    Gitt en person
    Når personen søker om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | søknadsdato                   | søknadId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | SØKT            |

  Scenariomal: Vedtak om innvilgelse gir status "INNVILGET"
    Gitt en person
    Når personen får vedtak om innvilgelse den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | INNVILGET       |

  Scenariomal: Vedtak om avslag gir status "AVSLÅTT"
    Gitt en person
    Når personen får vedtak om avslag den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | AVSLÅTT         |
