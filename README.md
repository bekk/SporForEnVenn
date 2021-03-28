# Spør for en venn
En vennlig bot som anonymt stiller spørsmål i en slack kanal

## Prerequisite
* Maven
* Azure cli 
* [Azure core tools](https://docs.microsoft.com/en-us/azure/azure-functions/functions-run-local?tabs=macos%2Ccsharp%2Cbash#v2)
* [ngrunk](https://ngrok.com/) Install with `brew install ngruk`

## Lokal utvikling
Lokal utvikling krever at du kjører en proxy 
og oppdaterer slack applikasjonen til å sende slash comandoen til
proxy urlen som er mulig å nå utenfor ditt lokale nettverk. 
[Lenke til slash comando for denne appen](https://api.slack.com/apps/A01SG4U1Q66/slash-commands?)

### Bygg
`mvn clean package`

### Kjør
`mvn azure-functions:run`

### Tips
For å kjøre denne applikasjonen så trenger du ngruk for å eksponere din localhost ut til slack.
Den ligger bare som en proxy og sender http kall videre til din applikasjon.
Jeg har satt det opp slik at functions serves på localhost:3000. 
Dette kan du endre i `local.settings.json`
```json
{
  "IsEncrypted": false,
  "Values": {
    "AzureWebJobsStorage": "",
    "FUNCTIONS_WORKER_RUNTIME": "java"
  },
  "Host": {
    "LocalHttpPort": 3000 // set din port her om du vil ha en annen
  }
}
```

### Mine aliaser:
```bash
alias build='mvn clean package'
alias run='mvn azure-functions:run'
alias deploy='mvn azure-functions:deploy'
alias forward='ngrok http 3000' // Denne må være lik den porten i configen
```
