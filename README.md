# dp-rapportering-personregister

Personregister over dagpengebrukere som sender meldekort. Dette registeret er en sentral komponent i NAVs dagpengesystem og håndterer statusinformasjon for brukere som mottar eller søker om dagpenger.

## Appens ansvar
- Registeret holder i dagpengerbrukernes status basert på innsendt søknad, arbeidssøkerstatus og vedtak i Arena.
- Dp-rapportering bruker registeret for å sjekke hvilke meldekortbrukere som skal videresendes fra meldekort-frontend til dp-rapportering-frontend.
- Registeret har ansvar for at dagpenger-meldekort blir opprettet for brukere (når arena ikke lenger oppretter meldekort).

## Arkitektur

Applikasjonen er bygget med en modulær arkitektur bestående av følgende komponenter:

### Moduler
- **modell**: Inneholder domenemodellen og hendelsesklasser
- **mediator**: Håndterer hendelser og oppdaterer domenemodellen
- **kafka**: Håndterer kommunikasjon med Kafka for å motta og sende hendelser
- **openapi**: Inneholder API-dokumentasjon

### Domenemodell
Kjernen i applikasjonen er `Person`-klassen som representerer en bruker i systemet. En person har:
- Identifikator (ident)
- Statushistorikk
- Arbeidssøkerperioder
- Meldegruppe
- Meldeplikt
- Hendelser

En person kan ha to statuser:
- `DAGPENGERBRUKER`: Bruker som mottar dagpenger
- `IKKE_DAGPENGERBRUKER`: Bruker som ikke mottar dagpenger

### Hendelseshåndtering
Applikasjonen bruker en hendelsesdrevet arkitektur hvor ulike hendelser mottas og behandles:
- `SøknadHendelse`: Håndterer innsending av søknad
- `DagpengerMeldegruppeHendelse`: Håndterer endringer i dagpenger-meldegruppe
- `AnnenMeldegruppeHendelse`: Håndterer endringer i andre meldegrupper
- `MeldepliktHendelse`: Håndterer endringer i meldeplikt
- `PersonSynkroniseringHendelse`: Håndterer synkronisering av persondata
- `ArbeidssøkerperiodeHendelse`: Håndterer endringer i arbeidssøkerperioder

## Integrasjoner
- **dp-soknad**: Bruker innsending av søknad for å opprette brukere i registeret. Innsending av søknad er et krav for å kunne sende meldekort.
- **dp-arbeidssokeregister-adapter**: Brukes for å hente arbeidssøkerstatus og perioder fra arbeidssøkerregisteret, samt å overta/frasi ansvaret for å bekrefte brukers arbeidssøkerperioder.
- **arena**: Bruker endring i vedtak i arena for å oppdatere brukerens status i registeret.

## Teknisk stack
- **Språk**: Kotlin
- **Byggeverktøy**: Gradle
- **Rammeverk**: Ktor
- **Meldingskø**: Kafka
- **Database**: PostgreSQL
- **Containerisering**: Docker
- **Deployment**: Kubernetes via NAIS-plattformen

## Oppsett og kjøring

### Forutsetninger
- JDK 17 eller nyere
- Docker og Docker Compose for lokal utvikling
- Tilgang til NAV's interne systemer for full funksjonalitet

### Bygging
```bash
./gradlew build
```

### Kjøring lokalt
```bash
./gradlew run
```

### Kjøring med Docker
```bash
docker build -t dp-rapportering-personregister .
docker run -p 8080:8080 dp-rapportering-personregister
```

## Forretningslogikk

### Scenarier
1. **Lytter på søknad**:
   1.1 Sjekker arbeidssøkerregister
   - Hvis bruker er registrert som arbeidssøker, får man status 'ARBS' og overtar bekreftelse av periode
   - Hvis bruker ikke er registrert som arbeidssøker, settes det ingen status
   1.2 Settes status 'Søkt'

2. **Lytter på Arbeidssøkerperiodehendelse**:
   2.1 Case registrert:
   - Sjekker om brukeren finnes i registeret
   - Hvis bruker finnes i registeret, settes status 'ARBS' og overtar bekreftelse av perioden

3. **Lytter på vedtak**:
   3.1 Case innvilget:
   - Sett status 'Innvilget'
   3.2 Case avslått:
   - Sett status 'Avslått'
   3.3 Case stanset:
   - Sett status 'Stanset'

## Krav for å være dagpengebruker
En person må oppfylle følgende krav for å få status som dagpengebruker:
- Være registrert som arbeidssøker
- Ha meldeplikt
- Være i meldegruppen "DAGP"

## Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
