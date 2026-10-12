# Passaggio di consegne: Google Maps sul Garmin Forerunner 255

Documento per riprendere il lavoro in una nuova sessione di Claude Code (in locale). Repo: `tmm07/garmin-maps`, branch `claude/garmin-255-google-maps-app-f64rv8`. Lingua dell'utente: italiano.

## L'obiettivo dell'utente, in ordine di importanza

1. **Funzionare come Apple Watch con l'iPhone, ma con Google Maps.** L'utente avvia la navigazione in Google Maps sul telefono (Android) e il telefono resta in tasca. Sul Forerunner 255 deve comparire tutto, **in automatico, senza altre impostazioni** ("quando faccio Avvia sul telefono compare tutto sull'orologio").
2. **Il telefono fa tutti i calcoli**, l'orologio mostra soltanto dove andare. Non vuole un'app che calcoli un proprio percorso.
3. **Una mappa sull'orologio**, come su Apple Watch (mappa con la propria posizione, banner con la prossima svolta in alto).
4. **La linea del percorso sulla mappa.** Richiesta per ultima, ancora aperta (vedi sotto).
5. **Gratuito.** Niente chiavi API a pagamento, niente carta di credito, niente server da mantenere.
6. Preferenza di metodo: **chiedere prima di fare** cose di una certa dimensione. L'utente ha interrotto una mia scelta fatta senza chiedere. Su questo punto ha poi detto: "fai come meglio ti sembra", purché sia tutto automatico.

Telefono dell'utente: **Android** (confermato). Su iPhone non si può fare: iOS non permette di leggere le notifiche di altre app.

## Come ci siamo arrivati (storia)

1. Prima versione: app Connect IQ che calcolava da sola percorsi e luoghi tramite un backend con le API Google. **Scartata**: l'utente voleva che fosse Google Maps sul telefono a fare tutto.
2. Seconda versione: stesso schema ma con backend gratuito su OpenStreetMap (OSRM, Overpass, Nominatim, tile OSM unite sul server). **Scartata** per lo stesso motivo.
3. Versione attuale (committata e pushata): app Android che legge la notifica di navigazione di Google Maps e il GPS del telefono, e li manda al 255 via Garmin Connect. Il vecchio `backend/` è stato cancellato.

## Architettura attuale

```
Google Maps (telefono) --notifica di navigazione--> app Android --Garmin Connect (Bluetooth)--> app sul 255
GPS del telefono + strade OpenStreetMap (Overpass) ---------^
```

- **`android/core`** (Kotlin puro, testabile): `NavParser` (da titolo/testo/sottotesto della notifica ricava distanza, istruzione, codice manovra, orario di arrivo; riconosce italiano e inglese), `MapEncoder` (strade -> stringa compatta), test in `CoreTest.kt`.
- **`android/app`**: `NavListener` (NotificationListenerService, legge `com.google.android.apps.maps`), `TrackerService` (servizio in primo piano: GPS ogni ~2 s, ricarica le strade ogni ~150 m), `WatchLink` (Connect IQ Mobile SDK: coda di messaggi, un messaggio alla volta, apre l'app sull'orologio), `Roads` (Overpass), `Bridge` (stato condiviso), `MainActivity` (permessi, Avvia/Ferma, registro di debug).
- **`watch/`** (Monkey C): `MapsApp` (riceve i messaggi, vibra), `Model` (stato e decodifica delle strade), `MapView` (mappa orientata nel verso di marcia, banner, pagina con freccia grande; SU/GIU = zoom, START = cambia pagina).
- ID app Connect IQ (deve coincidere in `watch/manifest.xml` e `WatchLink.APP_ID`): `8845fc29-fff0-4998-acda-f595d298093d`.

### Protocollo telefono -> orologio (dizionari con chiavi corte)

- `{"k":"n","d":"350 m","i":"Svolta a destra su Via X","m":2,"e":"12 min · 4,1 km","...}`: navigazione. `m` = manovra (0 dritto, 1 sx, 2 dx, 3 slight sx, 4 slight dx, 5 sharp sx, 6 sharp dx, 7 inversione, 8 rotonda, 9 arrivo, 10 sconosciuto).
- `{"k":"x"}`: navigazione finita.
- `{"k":"p","r":<id mappa>,"x":dm,"y":dm,"h":gradi}`: posizione in decimetri rispetto al riferimento della mappa (x est, y nord).
- `{"k":"m","r":<id>,"w":"<strade codificate>"}`: strade. Formato: linee separate da `;`; ogni linea = una cifra di classe + 4 caratteri per punto; ogni carattere = codice - 63 (6 bit); 2 caratteri = una coordinata a 12 bit, passi di 2 m, zero = 2048. Budget ~1400 caratteri perché il collegamento fa ~128 byte/s.

## Stato del lavoro

**Committato e pushato** (ultimo commit `aac8154`): tutto quanto sopra.

**Non testato su nessun dispositivo.** Nell'ambiente cloud non c'erano né l'Android SDK né il Connect IQ SDK, e Maven Central rifiutava i download (429), quindi nemmeno i test di `android/core` sono stati eseguiti. Il codice Kotlin e Monkey C non è mai stato compilato: aspettarsi errori al primo build.

**Preparazione per la linea del percorso (committata, non ancora usata):**
- `core/.../MapEncoder.kt`: `Way` ora ha `kind` (MINOR=0, MAJOR=1, ROUTE=2) al posto di `major: Boolean`; la classe ROUTE e' sempre inclusa e va per prima; aggiunta `segmentDistance`. `Roads.kt` e' stato adattato. Il lato orologio non disegna ancora la classe 2 (la tratta come strada minore).
- Nuovi `MapsLinkParser.kt` e `RouteTracker.kt` con test: servono solo all'opzione B (destinazione condivisa + percorso calcolato da noi). Se si sceglie A si possono cancellare.

## Il punto aperto: la linea del percorso

Google Maps **non espone** la sua linea blu a nessuna app. La notifica di navigazione contiene solo la **prossima** svolta; l'elenco completo dei passaggi esiste solo sullo schermo di Maps (leggerlo richiederebbe un servizio di accessibilità con la lista aperta a schermo, incompatibile col telefono in tasca). Opzioni valutate:

- **A. Ricostruzione dalle svolte (consigliata, coerente con "tutto automatico").** Con la geometria delle strade OSM (Overpass con `out geom`, includere gli id dei nodi per costruire il grafo): agganciare la posizione alla strada più vicina nella direzione di marcia (`heading`), seguirla per la distanza indicata da Maps ("350 m" già parsata nel `NavState`) fino alla svolta, entrare nella via che l'istruzione nomina ("su Via Roma"; altrimenti scegliere il ramo a sinistra/destra secondo la manovra) e proseguire ~150 m. Si disegna come linea di classe ROUTE nello stesso messaggio delle strade. È la linea di Google solo fino alla prossima svolta e un tratto oltre, non l'intero viaggio; può sbagliare agli incroci complicati o se i nomi OSM non coincidono.
- **B. Destinazione condivisa + percorso nostro.** L'utente condivide il luogo da Google Maps alla nostra app (`MapsLinkParser` estrae le coordinate, seguendo anche i link `maps.app.goo.gl`), si calcola il percorso intero con OSRM (routing.openstreetmap.de, gratuito, profili a piedi/bici/auto), `RouteTracker` segue l'avanzamento e ricalcola se si esce di strada. Linea intera, ma può essere diversa da quella di Google, e richiede un passaggio manuale: **contraddice il requisito "tutto in automatico"**.
- **C. Entrambe**: B quando c'è una destinazione, A altrimenti.

L'utente non ha scelto tra queste (ha chiuso la domanda) e ha detto "fai come meglio ti sembra" purché automatico. La scelta coerente è **A**.

## Altri punti aperti o rischiosi

1. **Avvio automatico.** Oggi l'utente deve aprire l'app Android e toccare "Avvia" una volta (serve per avviare il servizio con il GPS). Il requisito è che parta da solo quando si avvia la navigazione in Maps. Problema: da Android 12 un servizio in primo piano non si può avviare dal background; un `NotificationListenerService` non rientra tra le eccezioni sicure. Valutare: far partire il tracker da `NavListener` provando comunque (e verificare), oppure far restare l'app sempre attiva con servizio in primo piano avviato all'avvio del telefono (`BOOT_COMPLETED`) che attende la navigazione, oppure usare la posizione senza servizio in primo piano. Il lato orologio si apre già da solo (`openApplication`).
2. **Formato della notifica di Google Maps**: cambia per versione e lingua. Il `NavParser` è basato su ipotesi; va verificato con le notifiche reali (il `Registro` in `MainActivity` le mostra). Se il testo reale è diverso, adattare `NavParser`.
3. **Dipendenza Connect IQ per Android**: `com.garmin.connectiq:ciq-companion-app-sdk:2.0.3@aar` è citata a memoria e non verificata; in alternativa scaricare l'AAR da Garmin Developer. Un'app sideloaded potrebbe richiedere la modalità sviluppatore di Garmin Connect per i messaggi.
4. **Orologio**: la mappa è vettoriale (non immagini: `makeImageRequest` ha problemi noti sui dispositivi Connect IQ 4+). Prodotti nel manifest: `fr255`, `fr255s`, `fr255m`, `fr255sm`. L'icona del launcher è 40x40, da adattare se il compilatore avvisa.
5. **Memoria e banda**: poche decine di KB sull'orologio; mappa ogni ~150 m, si carica in qualche secondo.

## Prossimi passi suggeriti

1. Decidere se tenere `MapsLinkParser`/`RouteTracker` (servono solo all'opzione B).
2. Compilare davvero: Android Studio per `android/`, Connect IQ SDK per `watch/` (`monkeyc -d fr255 -f monkey.jungle -o Maps.prg -y <chiave>`). Eseguire `gradle :core:test` e correggere.
3. Provare con Google Maps reale e leggere il Registro: sistemare `NavParser`.
4. Implementare l'avvio automatico (punto 1).
5. Implementare la linea del percorso con l'opzione A (e solo su richiesta B).
