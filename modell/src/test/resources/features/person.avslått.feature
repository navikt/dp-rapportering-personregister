#language: no
Egenskap: Person
  Brukere som fikk avslag


  Scenariomal: Vedtak om innvilgelse gir status "INNVILGET"
    Gitt en person som fikk avslag den "<avslagsdato>" med avslagId "<avslagId>"
    Når personen får vedtak om innvilgelse den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | avslagsdato                   | avslagId                             | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000  | 2024-09-02T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614178000 | INNVILGET       |


  Scenariomal: Søknad etter avslag gir status "SØKT"
    Gitt en person som fikk avslag den "<avslagsdato>" med avslagId "<avslagId>"
    Når personen søker om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | avslagsdato                    | avslagId                              | søknadsdato                   | søknadId                             | forventetStatus |
      | 2024-08-30T11:00:27.899791748  | 123e4567-e89b-12d3-a456-426614174000  | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174001 | SØKT            |

