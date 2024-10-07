#language: no
Egenskap: Person
  Brukere som fikk avslag


  Scenariomal: Vedtak om stans gir status "STANSET"
    Gitt en person som har dagpengerrettighet
    Når personen får vedtak om stans den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | søknadsdato                   | søknadId                             | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | 2024-09-02T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614178000 | STANSET       |


