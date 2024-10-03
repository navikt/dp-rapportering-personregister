#language: no
Egenskap: Person
  Brukere som allerede er søker


  Scenariomal: Vedtak om innvilgelse gir status "INNVILGET"
    Gitt en person som søkt om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Når personen får vedtak om innvilgelse den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | søknadsdato                   | søknadId                             | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | 2024-09-02T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614178000 | INNVILGET       |


  Scenariomal: Vedtak om avslag gir status "AVSLÅTT"
    Gitt en person som søkt om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Når personen får vedtak om avslag den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | søknadsdato                   | søknadId                             | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | 2024-09-02T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614178000 | AVSLÅTT       |