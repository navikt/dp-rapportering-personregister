#language: no
Egenskap: Person
  Brukere som fikk vedtak om stans


  Scenariomal: Vedtak om innvilgelse gir status "INNVILGET"
    Gitt en person som fikk stans fra "<stansdato>"
    Når personen får vedtak om innvilgelse den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | stansdato                     | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 2024-09-02T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614178000 | INNVILGET       |


  Scenariomal: Søknad etter stans gir status "SØKT"
    Gitt en person som fikk stans fra "<stansdato>"
    Når personen søker om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | stansdato                      | søknadsdato                   | søknadId                             | forventetStatus |
      | 2024-08-30T11:00:27.899791748  | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | SØKT       |

