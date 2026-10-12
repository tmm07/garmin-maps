# Google Maps sul Forerunner 255 (Android)

Usi **Google Maps sul telefono** come sempre; il telefono resta in tasca e il 255 mostra dove andare,
come Apple Watch con l'iPhone: mappa che ruota nel verso di marcia con la tua posizione, e in alto
freccia, distanza e via della prossima svolta. Vibra a ogni nuova indicazione e quando sei vicino alla svolta.

```
Google Maps (telefono) --notifica di navigazione--> app Android --Bluetooth/Garmin Connect--> app sul 255
GPS del telefono + strade OpenStreetMap ---------------^
```

- Tutto il calcolo del percorso lo fa Google Maps. Niente server, niente costi, niente chiavi API.
- Freccia, distanza, istruzione e orario di arrivo vengono dalla notifica di navigazione di Google Maps.
- La mappa e' fatta dall'app Android: strade intorno a te da OpenStreetMap (Overpass, gratuito) in formato compatto,
  e posizione/direzione dal GPS del telefono. L'orologio le disegna da solo (come fa Apple Watch).
- Google Maps non rende disponibile il percorso (la linea blu), quindi **sulla mappa non c'e' la linea del percorso**:
  vedi le strade e te stesso, e il banner ti dice dove svoltare.
- Solo Android: iPhone non permette di leggere le notifiche di altre app.

## Installazione

1. **App Android** (`android/`): apri la cartella in Android Studio, collega il telefono, Run.
   Serve l'app *Garmin Connect* sul telefono, con il 255 gia' associato.
2. **App per l'orologio** (`watch/`): installa il [Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/), poi
   `cd watch && monkeyc -d fr255 -f monkey.jungle -o Maps.prg -y /percorso/developer_key.der`
   (`fr255s` per il 255S) e copia `Maps.prg` in `GARMIN/APPS/` dell'orologio via USB.
3. Sul telefono apri *Maps per Garmin* e fai i tre passi: consenti l'accesso alle notifiche, consenti posizione e
   notifiche, tocca **Avvia**. Il servizio resta attivo finche' non lo fermi.
4. Avvia la navigazione in Google Maps: l'app si apre da sola sull'orologio.

Comandi sull'orologio: SU/GIU = zoom della mappa, START = passa tra mappa e freccia grande.

## Stato: leggi prima di fidarti

Niente di questo e' stato provato su un telefono o su un orologio: qui non ci sono ne' Android SDK ne' Connect IQ SDK.
I test di `android/core` (parser della notifica e codifica della mappa) sono scritti ma non li ho potuti eseguire
(`gradle :core:test`). Punti piu' a rischio:

- **Formato della notifica di Google Maps.** Cambia tra versioni e lingue. Se sull'orologio non compare nulla, guarda il
  *Registro* nell'app Android: mostra titolo/testo/sottotesto di ogni notifica di Maps. Mandameli e adatto il parser.
  La freccia viene dedotta dal testo ("a destra", "rotatoria"...), funziona in italiano e inglese.
- **Dipendenza Connect IQ per Android** (`com.garmin.connectiq:ciq-companion-app-sdk`): se Gradle non la trova, scarica l'AAR
  dal sito Garmin Developer e mettila in `app/libs/`. Una app installata a mano (non dallo store) potrebbe richiedere
  la modalita' sviluppatore di Garmin Connect per scambiare messaggi.
- **Velocita' del Bluetooth** (~128 byte/s): la mappa si aggiorna ogni ~150 m e ci mette qualche secondo ad arrivare.
- **Batteria**: il GPS del telefono resta acceso mentre il servizio e' attivo.
