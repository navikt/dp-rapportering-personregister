# dp-rapportering-personregister
Personregister over dagpengebrukere som sender meldekort.

## Appens ansvar
- Registeret holder i dagpengerbrukernes status basert på innsendt søknad, arbeidssøkerstatus og vedtak i Arena eller DP-Sak.
- Meldekort-frontend bruker registeret for å sjekke hvilke meldekortbrukere som skal videresendes til dp-rapportering-frontend.
- Registeret har ansvar for at dagpenger-meldekort blir opprettet for brukere (når arena ikke lenger oppretter meldekort).

## Teknologier

- Kotlin
- Gradle
- Ktor
- Kafka
- Postgres
- JUnit
- OpenAPI

## Formål
Systemet samler og eksponerer et nå-bilde av om en person er dagpengerbruker ("DAGPENGERBRUKER") eller ikke. Tjenesten:
- Konsoliderer hendelser fra flere kilder (søknad, arbeidssøkerregister, vedtak/behandlingsresultat (DP-Sak), meldeplikt/meldegruppe (Arena)).
- Holder historikk (status, hendelser, perioder) på person.
- Produserer Kafka-meldinger (på vegne av) for å overta eller frasi ansvar for brukers arbeidssøkerperiode.
- Sender start/stopp-meldinger til Meldekortregister for å starte eller stoppe meldekortproduksjonen for bruker.
- Eksponerer API-er for bruk internt og av saksbehandler.

## Statusbestemmelse
En person anses som DAGPENGERBRUKER når:
1. Personen er arbeidssøker (har en aktiv arbeidssøkerperiode) OG
2. (Arena-regime) meldeplikt = true OG meldegruppe = DAGP
   ELLER (DP-regime) harRettTilDp = true

Ellers: IKKE_DAGPENGERBRUKER.
AnsvarligSystem settes default til Arena. Hvis bruker får et positivt vedtak fra DP-Sak settes DP som ansvarlig system.

## Viktige komponenter
### Mediatorer
Mediator-laget brukes for orkestrering og domenelogikk, og sørger for at sideeffekter (lagring, observatører, eksterne kall) samles ett sted.
- `PersonMediator` – sentral koordinering av personrelaterte hendelser (søknad, vedtak, meldegruppe, synkronisering, meldesyklus passert).
- `ArbeidssøkerMediator` – håndterer arbeidssøkerperioder og overtakelse/frasigelse.
- `MeldepliktMediator` – håndterer meldepliktendringer.
- `MeldestatusMediator` – prosesserer meldeplikt/meldegruppe ved melding om meldestatusendring fra Arena.
- `FremtidigHendelseMediator` – lagrer hendelser med startdato i fremtiden i egen databasetabell.

### Observers
Observere reagerer på endringer i Person og produserer sideeffekter:
- `PersonObserverKafka` – produserer Kafka-meldinger (bl.a. bekreftelse på vegne av).
- `ArbeidssøkerBeslutningObserver` – persisterer beslutninger knyttet til arbeidssøkerstatus.
- `PersonObserverMeldekortregister` – interaksjon med Meldekortregister (start/stopp av meldekortproduksjon).

### Connectors (eksterne avhengigheter)
- `ArbeidssøkerConnector` – henter perioder / record key fra arbeidssøkerregisteret.
- `MeldepliktConnector` – henter meldestatus og meldeplikt fra meldeplikt-adapter.
- `MeldekortregisterConnector` – oppdaterer meldekortregister ved oppdatering og konsolidering av identer på bakgrunn av endringer i PDL.
- `PdlConnector` – henter identhistorikk/fødselsnummer fra PDL.

### Repository
- `PostgresPersonRepository` – persisterer person, hendelser, fremtidige hendelser, arbeidssøkerperioder og statushistorikk.

### Jobber
- `AktiverHendelserJob` (daglig ~00:01) – aktiverer fremtidige hendelser (meldeplikt/meldegruppe/vedtak) som starter i dag.

#### Midlertidige jobber
- `AvvikPersonsynkroniseringJob` – sjekker og logger avvik i personsynkronisering (f.eks. feil meldegruppe i forhold til status) for alle personer. Jobben logger kun avvik; Denne jobben ble brukt når vi hadde problemer med sync mot Arena og brukes ikke lenger.
- `AvvikStatusJob` – beregner «riktig» personstatus ut fra hendelser og arbeidssøkerperioder og retter opp personer med statusavvik; Denne jobben ble brukt når vi hadde problemer med sync mot Arena og brukes ikke lenger.
- `MeldestatusJob` – henter meldestatus fra Arena for personer som ligger i `temp_person`-tabellen og oppdaterer status via `MeldestatusMediator`; Denne jobben ble brukt for å synce personer mot Arena. Jobben er aktiv og vil aktivt plukke personer fra `temp_person`-tabellen.
- `ResendPåVegneAvMelding` – finner personer med avvik i bekreftelsesansvar og re-sender overtakelses-/frasigelsesmeldinger på Kafka til alt er i synk; Jobben er aktiv og kjører periodisk (hvert 40. minutt).

## Hendelsesstrøm / Mottak
Systemet lytter på Rapid (Rapids & Rivers) og Kafka.
Mottaksklasser konverterer innkommende meldinger til domenespesifikke hendelser:
- `SøknadMottak` – søknad innsending -> `SøknadHendelse`.
- `BehandlinsresultatMottak` / `VedtakFattetUtenforArenaMottak` – vedtak -> `VedtakHendelse`
- `MeldestatusMottak` – tar i mot endringemeldinger fra Arena.
- `MeldesyklusErPassertMottak` - varsler om at en meldekortsyklus til en bruker er passert.
- `NødbremsMottak` – styrer midlertidig stopp.
- `ArbeidssøkerMottak` & `ArbeidssøkerperiodeOvertakelseMottak` – endringer i perioder og "kvittering" for meldinger om overtakelse/frasigelse.

## API
OpenAPI-spesifikasjon: `openapi/src/main/resources/personstatus-api.yaml`.
Hostes [her](https://navikt.github.io/dp-rapportering-personregister/) via Github Pages.

## Integrasjoner
| System / Tjeneste | Formål                                                     |
|-------------------|------------------------------------------------------------|
| Arbeidssøkerregister | Arbeidssøkerperioder / record key                           |
| Meldeplikt-adapter | Hente meldestatus (meldegruppe/harMeldtSeg/meldeplikt)     |
| Meldekortregister | Start / Stopp / Overtakelse / Frasigelse av bekreftelsesansvar |
| PDL | Ident-historikk og personoppslag                           |

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
