# Google Maps per Garmin Forerunner 255

App Connect IQ che porta su un Forerunner 255 un'interfaccia in stile Apple Watch Maps, con mappe Google.

```
Forerunner 255  <--Bluetooth-->  Garmin Connect (telefono)  <--HTTPS-->  backend/  <-->  Google (Routes, Places, Maps Static)
```

Il 255 non ha mappe offline e Google non offre un SDK per Garmin, quindi la mappa viene
disegnata dal server (Maps Static API, con il percorso sopra) e l'orologio la scarica come immagine.

## Cosa fa

- **Home**: mappa Google attorno a te (puntino blu). SU/GIU = zoom, START = menu.
- **Menu**: modalita' (a piedi / auto / bici), 3 preferiti (indirizzi o nomi, li imposti da Garmin Connect), ricerca vicino a te (ristoranti, bar, farmacie, benzina, parcheggi, bancomat, supermercati).
- **Anteprima percorso**: tutto il tragitto sulla mappa, tempo e distanza, START per partire.
- **Navigazione**: mappa a schermo intero con il percorso, banner in alto con freccia, distanza e istruzione della prossima svolta, vibrazione alla svolta, ricalcolo se esci dal percorso. START = freccia grande a tutto schermo. SU/GIU = zoom.
- Se la mappa non si scarica, la navigazione ripiega su una mappa vettoriale semplificata.

Non c'e' la ricerca a voce/tastiera: il 255 non ha un modo comodo per scrivere, per questo ci sono preferiti e categorie.

## 1. Chiavi Google

Nella Google Cloud Console crea un progetto con fatturazione attiva e abilita: **Routes API**, **Places API (New)**, **Maps Static API**. Crea una chiave API (limitala a queste tre API). Ogni mappa scaricata e' una richiesta Static Maps, controlla i prezzi e metti un budget alert.

## 2. Backend

Serve un server con HTTPS valido (l'orologio rifiuta certificati non validi): Render, Fly.io, Cloud Run, un VPS con Caddy...

```
cd backend
GOOGLE_API_KEY=... APP_TOKEN=una-stringa-lunga-a-caso npm start
npm test
```

## 3. App per l'orologio

1. Installa il [Connect IQ SDK](https://developer.garmin.com/connect-iq/sdk/) e crea la developer key (`monkeyc` / estensione VS Code "Monkey C").
2. `cd watch && monkeyc -d fr255 -f monkey.jungle -o GoogleMaps.prg -y /percorso/developer_key.der` (usa `fr255s` per il 255S, ecc.).
3. Collega l'orologio via USB e copia `GoogleMaps.prg` in `GARMIN/APPS/`.
4. In Garmin Connect (telefono) > dispositivo > App > Google Maps > Impostazioni: inserisci **URL server** (https://...), **Token** (= `APP_TOKEN`), modalita' e preferiti.

## Stato

Il backend ha test automatici. **L'app per l'orologio non e' stata compilata ne' provata su un dispositivo**: in questo ambiente non c'e' l'SDK Connect IQ. Aspettati qualche correzione al primo build. Il punto piu' delicato e' la memoria: l'immagine mappa e' `MapApi.px()` pixel di lato (max 240 in `watch/source/Api.mc`); se l'orologio da' "Out of memory" abbassala. L'icona del launcher e' 40x40, adattala se il compilatore avvisa.
