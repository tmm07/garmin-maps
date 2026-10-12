# Google Maps per Garmin Forerunner 255

App Connect IQ che porta su un Forerunner 255 un'interfaccia in stile Apple Watch Maps, con mappe Google.

```
Forerunner 255  <--Bluetooth-->  Garmin Connect (telefono)  <--HTTPS-->  backend/  <-->  OpenStreetMap
```

Tutto gratuito, senza chiavi API e senza carta di credito: percorsi da OSRM (routing.openstreetmap.de),
luoghi da Overpass, indirizzi da Nominatim. Il 255 non ha mappe offline, quindi la mappa la disegna il
backend (tile OpenStreetMap unite, percorso sopra, colori ridotti a quelli dello schermo) e l'orologio
la scarica come immagine PNG.

## Cosa fa

- **Home**: mappa Google attorno a te (puntino blu). SU/GIU = zoom, START = menu.
- **Menu**: modalita' (a piedi / auto / bici), 3 preferiti (indirizzi o nomi, li imposti da Garmin Connect), ricerca vicino a te (ristoranti, bar, farmacie, benzina, parcheggi, bancomat, supermercati).
- **Anteprima percorso**: tutto il tragitto sulla mappa, tempo e distanza, START per partire.
- **Navigazione**: mappa a schermo intero con il percorso, banner in alto con freccia, distanza e istruzione della prossima svolta, vibrazione alla svolta, ricalcolo se esci dal percorso. START = freccia grande a tutto schermo. SU/GIU = zoom.
- Se la mappa non si scarica, la navigazione ripiega su una mappa vettoriale semplificata.

Non c'e' la ricerca a voce/tastiera: il 255 non ha un modo comodo per scrivere, per questo ci sono preferiti e categorie.

## 1. Backend gratuito

Serve un server con HTTPS valido (l'orologio rifiuta certificati non validi). Opzioni gratuite:

- **Il tuo PC o un Raspberry + Cloudflare Tunnel** (gratis, HTTPS incluso; l'app funziona solo quando il PC e' acceso).
- **Render / Fly.io / Koyeb, piano gratuito**: sempre raggiungibile, ma il piano gratuito va in pausa quando non e' usato, quindi la prima richiesta puo' metterci piu' di 30 secondi.

```
cd backend
npm install
APP_TOKEN=una-stringa-lunga-a-caso CONTACT=tua@email.it npm start
npm test
```

`CONTACT` finisce nello User-Agent: le regole d'uso di OpenStreetMap chiedono di essere identificabili.

**Regole d'uso.** I server pubblici di OpenStreetMap sono per uso leggero e personale, che e' il tuo caso
(le tile sono messe in cache). Per uso intensivo, o se qualcuno blocca il tuo server, imposta `TILE_URL`
con un provider di tile con piano gratuito (es. MapTiler, Stadia) e non usare i server pubblici.

## 2. App per l'orologio

1. Installa il [Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/) e crea la developer key (`monkeyc` / estensione VS Code "Monkey C").
2. `cd watch && monkeyc -d fr255 -f monkey.jungle -o GoogleMaps.prg -y /percorso/developer_key.der` (usa `fr255s` per il 255S, ecc.).
3. Collega l'orologio via USB e copia `GoogleMaps.prg` in `GARMIN/APPS/`.
4. In Garmin Connect (telefono) > dispositivo > App > Google Maps > Impostazioni: inserisci **URL server** (https://...), **Token** (= `APP_TOKEN`), modalita' e preferiti.

## Stato

Il backend ha test automatici, ma sono fatti con servizi simulati: **non l'ho provato contro i server OpenStreetMap veri** (da questo ambiente erano bloccati). **L'app per l'orologio non e' stata compilata ne' provata su un dispositivo**: in questo ambiente non c'e' l'SDK Connect IQ. Aspettati qualche correzione al primo build. Il punto piu' delicato e' la memoria: l'immagine mappa e' `MapApi.px()` pixel di lato (max 240 in `watch/source/Api.mc`); se l'orologio da' "Out of memory" abbassala. L'icona del launcher e' 40x40, adattala se il compilatore avvisa.
