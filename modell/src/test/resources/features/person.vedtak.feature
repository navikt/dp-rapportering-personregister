#language: no
Egenskap: Person
  Brukere som får dagpengervedtak skal få oppdatert status

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

  Scenariomal: Klage på avslag med medhold endrer status til "INNVILGET".
    Gitt en person som fikk avslag
    Når personen klager og får medhold den "<vedtakdato>" med vedtakId "<vedtakId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | vedtakdato                    | vedtakId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | INNVILGET         |

  Scenariomal: Søknad endrer ikke status
    Gitt en person har dagpengerrettighet
    Når personen søker om dagpenger den "<søknadsdato>" med søknadId "<søknadId>"
    Så skal status være "<forventetStatus>"

    Eksempler:
      | søknadsdato                   | søknadId                             | forventetStatus |
      | 2024-09-01T11:00:27.899791748 | 123e4567-e89b-12d3-a456-426614174000 | INNVILGET            |
