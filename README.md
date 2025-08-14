# dp-rapportering-personregister
Personregister over dagpengebrukere som sender meldekort.

## Appens ansvar
- Registeret holder i dagpengerbrukernes status basert på innsendt søknad, arbeidssøkerstatus og vedtak i Arena.
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

## Integrasjoner
- dp-soknad: Bruker innsending av søknad for å opprette brukere i registeret. Innsending av søknad er et krav for å kunne sende meldekort.
- dp-arbeidssokeregister-adapter: Brukes for å hente arbeidssøkerstatus og perioder fra arbeidssøkerregisteret, samt å overta/frasi ansvaret for å bekrefte brukers arbeidssøkerperioder.
- arena: Bruker endring i vedtak i arena for å oppdatere brukerens status i registeret.

## App-arkitektur

### API

Appen bruker OpenAPI for å definere og dokumentere API-et. OpenAPI-spesifikasjonen ligger i [personstatus-api.yaml](openapi/src/main/resources/personstatus-api.yaml).

### Connectors

#### ArbeidssøkerConnector

> Henter siste arbeidssøkerperiode og record key fra Arbeidssøkerregister for en gitt bruker

### Repository

#### PersonRepository

> Alle kall til databasen som har med noe å gjøre med personer, hendelser og fremtidige hendelser er her. Dette innebærer lagring, henting, oppdatering og sletting.

### Service

#### ArbeidssøkerService

> Service som brukes for å utføre operasjoner mot Arbeidssøkerregister. Denne serviceklassen er en mellomklasse mellom ArbeidssøkerMediator og ArbeidssøkerConnector.

### Mottak

#### ArbeidssøkerMottak

> Mottar meldinger om Arbeidssøkerperioder og sender til ArbeidssøkerMediator for behandling.

#### MeldegruppeendringMottak

> Mottar meldinger om meldegruppeendringer, konverterer til hendelser og sender til PersonMediator for behandling eller til FremtidigHendelseMediator hvis hendelses startdato ligger i fremtiden.

#### MeldepliktendringMottak

> Mottar meldinger om meldepliktendringer, konverterer til hendelser og sender til PersonMediator for behandling eller til FremtidigHendelseMediator hvis hendelses startdato ligger i fremtiden.

#### SøknadMottak

> Mottar meldinger om innsendte søknader, konverterer til SøknadHendelse og sender til PersonMediator for behandling.

### Jobber som kjører regelmessig

#### AktiverHendelserJob

> Denne jobben kjører hver dag ved midnatt. Den aktiverer fremtidige hendelser, dvs. prosesserer fremtidige hendelser som har en startdato lik dagens dato.

#### SlettPersonerJob

> Denne jobben kjører hver dag kl. 01:00 og sletter fra Personregister personer som Personregister ikke er interresert i, dvs. personer som ikke er dagpengerbrukere og ikke har hendelser i de siste 60 dagene og ikke har relevante fremtidige hendelser.

## Domene

### Person

Person-klassen representerer en bruker i DP-systemet og har ansvaret for å holde oversikt over dagpengerbrukernes status basert på innsendte søknader, arbeidssøkerstatus og vedtak i Arena.
Klassen inneholder:
- **ident:** personens fødselsnummer.
- **statushistorikk:** en tidslinje med statusendringer (DAGPENGERBRUKER eller IKKE_DAGPENGERBRUKER).
- **arbeidssøkerperioder:** en liste over perioder personen har vært arbeidssøker.
- **meldegruppe og meldeplikt:** tilleggsinformasjon om rapporteringsstatus.
- **hendelser:** en logg over hendelser knyttet til personen, som kan behandles.
- **observatører:** objekter som varsles ved visse endringer, f.eks. når en bekreftelse overtas.
- **Metoder for statusendring:** inkluderer funksjoner som tillater endring av personens status og oppdatering av tilhørende informasjon basert på nye hendelser eller data.
- **Metoder for arbeidssøker bekreftelse:** inkluderer funksjoner for å overta eller frasi arbeidssøker bekreftelse.
- **Metoder for å prosessere hendelser:** inkluderer funksjoner for å prosessere hendelse om nye arbeidssøkerperioder.

### Arbeidssøkerperiode

Arbeidssøkerperiode-klassen representerer en periode der en person er registrert som arbeidssøker.
Klassen inneholder:
- **periodeId:** et unikt ID (UUID)
- **ident:** personens fødselsnummer
- **startet:** starttidspunkt for perioden
- **avsluttet:** eventuell sluttdato (kan være null)
- **overtattBekreftelse:** om bekreftelse er overtatt (kan være null)
- **Metoder:** inkluderer funksjoner for å sjekke at en periode er aktiv (dvs. ikke avsluttet) og finne første aktiv periode

Det finnes også to tilknyttede hendelsesklasser:
- **StartetArbeidssøkerperiodeHendelse:** registrerer starten på en periode, oppdaterer personens status og overtar arbeidssøker bekreftelse.
- **AvsluttetArbeidssøkerperiodeHendelse:** registrerer slutten på en periode, oppdaterer personens status og frasier seg arbeidssøker bekreftelse.

Disse klassene utvider ArbeidssøkerperiodeHendelse-abstraktklassen som i sin tur implementerer Hendelse-grensesnitt.
I tillegg til alle feltene i Hendelse-grensesnittet inneholder ArbeidssøkerperiodeHendelser:
- **periodeId:** et unikt ID (UUID)
- **startet:** starttidspunkt for perioden
- **avsluttet:** sluttdato for perioden (kun i AvsluttetArbeidssøkerperiodeHendelse)

### Hendelse

Hendelse er et grensesnitt som definerer en felles kontrakt for ulike hendelsestyper knyttet til en person i systemet.
Den inneholder:
- **ident:** hvilken person hendelsen gjelder (fødselsnummer)
- **dato:** når hendelsen skjedde
- **kilde:** hvilket system hendelsen kommer fra
- **referanseId:** en unik identifikator for hendelsen
- **behandle():** en funksjon som iverksetter effekter på en Person.

Konkrete implementasjoner inkluderer:
- AnnenMeldegruppeHendelse
- AvsluttetArbeidssøkerperiodeHendelse
- DagpengerMeldegruppeHendelse
- MeldepliktHendelse
- MeldesyklusErPassertHendelse
- PersonIkkeDagpengerSynkroniseringHendelse
- PersonSynkroniseringHendelse
- StartetArbeidssøkerperiodeHendelse
- SøknadHendelse
- VedtakHendelse

Kildesystem kan være:
- Søknad
- Arena
- Arbeidssokerregisteret
- Dagpenger

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
