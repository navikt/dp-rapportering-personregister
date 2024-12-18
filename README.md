# dp-rapportering-personregister
Personregister over dagpengebrukere som sender meldekort.

## Appens ansvar
- Registeret holder i dagpengerbrukernes status basert på innsendt søknad, arbeidssøkerstatus og vedtak i Arena.
- Dp-rapportering bruker registeret for å sjekke hvilke meldekortbrukere som skal videresendes fra meldekort-frontend til dp-rapportering-frontend.
- Registeret har ansvar for at dagpenger-meldekort blir opprettet for brukere (når arena ikke lenger oppretter meldekort).

## Integrasjoner
- dp-soknad: Bruker innsending av søknad for å opprette brukere i registeret. Innsending av søknad er et krav for å kunne sende meldekort.
- dp-arbeidssokeregister-adapter: Brukes for å hente arbeidssøkerstatus og perioder fra arbeidssøkerregisteret, samt å overta/frasi ansvaret for å bekrefte brukers arbeidssøkerperioder.
- arena: Bruker endring i vedtak i arena for å oppdatere brukerens status i registeret.

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
