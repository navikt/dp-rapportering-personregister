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
   ELLER (DP-regime) vedtak = INNVILGET

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
- `ResendPåVegneAvMelding` (hvert 40. minutt) – re-produserer meldinger om overtakelse/frasigelse på Kafka ved behov.

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

Autentisering:
- TokenX (sluttbruker/innlogget bruker) for `/personstatus` (GET/POST).
- Azure AD for saksbehandler-endepunkter `/hentPersonId`, `/hentIdent`.

## Integrasjoner
| System / Tjeneste | Formål                                                     |
|--------------------|------------------------------------------------------------|
| Arbeidssøkerregister | Arbeidssøkererioder / record key                           |
| Meldeplikt-adapter | Hente meldestatus (meldegruppe/harMeldtSeg/meldeplikt)     |
| Meldekortregister | Start / Stopp / Overtakelse / Frasigelse av bekreftelsesansvar |
| PDL (GraphQL) | Ident-historikk og personoppslag                           |
| Rapid & Rivers | Hendelsesbus for dagpengerdomene                           |
| Kafka (topics for arbeidssøkerperioder & bekreftelse-på-vegne-av) | Produksjon og konsumpsjon av strukturert avro-data         |
| Azure AD / TokenX | Autentisering / autorisasjon                               |
| Unleash | Feature toggles                                            |
| Postgres | Lagring av domene- og historikkdata                        |

## Datamodell (forenklet begrepsoversikt)
- Person(ident, statusHistorikk, arbeidssøkerperioder, vedtak, meldeplikt, meldegruppe, ansvarligSystem, hendelser)
- Arbeidssøkerperiode(periodeId, startet, avsluttet, overtattBekreftelse)
- Hendelser (SøknadHendelse, VedtakHendelse, Startet/AvsluttetArbeidssøkerperiodeHendelse, (Annen|Dagpenger)MeldegruppeHendelse, MeldepliktHendelse, Person(Synkronisering|IkkeDagpengerSynkronisering)Hendelse, MeldesyklusErPassertHendelse, NødbremsHendelse)
- Fremtidige hendelser (lagres separat til aktiveringstidspunkt)

## Viktige miljøvariabler
Obligatoriske (eksempler – se `Configuration.kt`):
- `BEKREFTELSE_PAA_VEGNE_AV_TOPIC`
- `ARBEIDSSOKERPERIODER_TOPIC`
- `KAFKA_BROKERS`, `KAFKA_SCHEMA_REGISTRY`, `KAFKA_SCHEMA_REGISTRY_USER`, `KAFKA_SCHEMA_REGISTRY_PASSWORD`
- `ARBEIDSSOKERREGISTER_OPPSLAG_HOST`, `ARBEIDSSOKERREGISTER_OPPSLAG_SCOPE`
- `ARBEIDSSOKERREGISTER_RECORD_KEY_HOST`, `ARBEIDSSOKERREGISTER_RECORD_KEY_SCOPE`
- `MELDEPLIKT_ADAPTER_HOST`, `MELDEPLIKT_ADAPTER_SCOPE`
- `MELDEKORTREGISTER_HOST`, `MELDEKORTREGISTER_SCOPE`
- `PDL_API_HOST`, `PDL_API_SCOPE`
- `UNLEASH_SERVER_API_URL`, `UNLEASH_SERVER_API_TOKEN`, `UNLEASH_SERVER_API_ENV`
- Azure AD klientkonfig (client id/secret via standard NAIS env)

Optional / defaulted:
- `KAFKA_RESET_POLICY` (default `earliest`)
- `KAFKA_CONSUMER_GROUP_ID` (default settes i kode)

## Kjøring lokalt (forenklet)
1. Start avhengigheter (Postgres + evt. lokal Kafka) – i dag forventes NAIS miljø normalt; for lokal debugging kan Testcontainers brukes via eksisterende tester.
2. Sett nødvendige miljøvariabler / .env.
3. Bygg:
```bash
./gradlew clean build
```
4. Kjør mediator-app (shadow jar):
```bash
./gradlew :mediator:run
# eller
java -jar mediator/build/libs/mediator-all.jar
```
(Autentisering lokalt krever gyldige tokens; bruk interne testverktøy.)

## Logging og metrikker
- Prometheus-endepunkt: `/metrics`
- Standard liveness/readiness: `/isAlive`, `/isReady`
- Domene-metrikker (arbeidssøkerperioder, vedtak, meldeplikt, synkronisering, database) publiseres med prefiks definert i `Metrikker`.

## Sikkerhet
- TokenX for sluttbruker-endepunkt (personstatus) – aud må være `cluster:teamdagpenger:dp-rapportering-personregister`.
- Azure AD for saksbehandler-endepunkter – krever korrekt scope.
- All kommunikasjon mot eksterne tjenester skjer med klient-legitimasjon (client credentials flow) via gjenbrukt cachet Oauth2-klient.

## Teknologier
- Kotlin (JVM 21)
- Ktor (server + CIO/Netty modulbruk)
- Kafka (Avro + Schema Registry)
- Rapids & Rivers
- Postgres (Kotliquery)
- Micrometer + Prometheus
- OpenAPI 3
- Unleash
- OAuth2 / TokenX / Azure AD

## Videre forbedringer (ideer)
- Ekstern cache (Valkey) for PDL ident-cache (TODO i kode).
- Aggregerte historikkendepunkter (ikke bare nåværende status).
- Eksplicit dokumentasjon av Kafka-skjema og topics.
- Evt. re-introduksjon av slettejobb om datavolum krever det.

## Henvendelser
Spørsmål om løsningen:
- Slack: `#dagpenger`
- Team: `#team-dagpenger-rapportering`

## Lisens
Se `LICENSE.md`.
