# EquiShare

EquiShare è un'app Android progettata per dividere facilmente le spese tra gruppi di amici, coinquilini o colleghi.  
L'app permette di tracciare le spese condivise, calcolare automaticamente i debiti tra utenti e visualizzare statistiche e attività del gruppo.

EquiShare non è solo un semplice "split bill app", ma integra funzionalità avanzate come:
- tracciamento delle spese su mappa
- caricamento di scontrini tramite fotocamera
- grafici sulle spese
- notifiche intelligenti

L'obiettivo è rendere la gestione delle spese di gruppo semplice, trasparente e anche divertente.

---

# Concept dell'app

Gli utenti possono:

- creare **gruppi di spesa** (vacanze, casa, eventi)
- aggiungere **spese condivise**
- dividere automaticamente i costi
- vedere **chi deve pagare chi**
- visualizzare **grafici delle spese**
- scattare foto agli **scontrini**
- vedere su **mappa dove sono avvenute le spese**
- guadagnare **punti e badge**

---
# Funzionalità dell'app

| Funzionalità | Punteggio Max | Utilizzo |
|----------------|---------------|----------|
| Autenticazione<br><small>Login, registrazione, accesso tramite provider esterni, ...</small> | 6 | **X** |
| Pagina di profilo dell’utente | 2 | **X** |
| Utilizzo della fotocamera<br><small>Tramite intent o con CameraX</small> | 3 | **X** |
| Utilizzo del GPS | 2 | **X** |
| Persistenza dati<br><small>- Su database (Room / CoreData, max 2 punti)<br>- In coppie chiave-valore (DataStore / UserDefaults, max 1 punto)</small> | 3 | **X** |
| Pagina con una lista di item | 2 | **X** |
| Pagina di dettaglio di un singolo item | 2 | **X** |
| Funzionalità di ricerca/filtri | 1 | **X** |
| Preferiti (aggiunta + visualizzazione) | 2 | **X** |
| Impostazioni con toggle tema chiaro/scuro/automatico | 2 | **X** |
| Mockup<br><small>Potete usare software come Figma o Balsamiq, o anche crearli in versione cartacea</small> | 2 | **X** |
| Mappe<br><small>OpenStreetMap, Google Maps, con marker personalizzati, informazioni in overlay …</small> | 3 | **X** |
| Tracking dell’utente su mappa | 3 | **X** |
| Gamification<br><small>Punti, badge, classifiche, …</small> | 3 | **X** |
| Grafici / data visualization | 2 | **X** |
| Intent per interagire con app esterne (telefono, calendario, …)<br><small>Uno o più tra questi, preferibilmente non visto a lezione</small> | 2 | **X** |
| Richiesta HTTP ad API esterne (es. OSM) | 3 | **X** |
| Lettura e/o scrittura dati su storage<br><small>Es. selezione foto profilo</small> | 2 | **X** |
| Notifiche in-app e/o push | 3 | **X** |
| Uso di sensori biometrici | 3 | **X** |
| Interazione con dispositivi wearable | 3 | |


---

# Totale punti

| Categoria | Punti |
|-----------|------|
| Funzionalità selezionate | **49 punti** |

Totale richiesto: **32 punti**  
Totale progetto: **49 punti**

---

# Architettura tecnica

L'app sarà sviluppata seguendo una architettura moderna Android.

**Pattern**

```
MVVM + Repository Pattern
```

**Tecnologie**

- Kotlin
- Jetpack Compose
- Room Database
- DataStore
- Retrofit / Ktor
- Google Maps SDK
- CameraX
- BiometricPrompt API
- WorkManager per notifiche

---

# Struttura dell'app

## Schermate principali

### Login / Registrazione
- login email/password
- login Google
- login biometrico

### Home
- lista gruppi
- riepilogo debiti
- notifiche recenti

### Gruppo
- lista spese
- saldo tra membri
- grafico spese

### Aggiungi spesa
- importo
- descrizione
- partecipanti
- foto scontrino
- posizione GPS

### Dettaglio spesa
- chi ha pagato
- chi deve
- foto scontrino
- posizione su mappa

### Mappa
- visualizza tutte le spese su mappa
- marker personalizzati
- tracking posizione utente

### Profilo utente
- foto profilo
- statistiche
- badge ottenuti
- spese totali

### Impostazioni
- tema chiaro/scuro
- notifiche
- privacy

---

# Gamification

Sistema per rendere l'app più coinvolgente.

Badge possibili:

- **The Banker** → ha pagato più spese
- **The Organizer** → ha creato molti gruppi
- **Receipt Master** → carica molti scontrini
- **Traveler** → spese in molte città

Leaderboard nei gruppi:

- chi paga più spesso
- chi deve meno soldi

---

# Mappe e GPS

Ogni spesa può salvare:

- latitudine
- longitudine

Funzionalità:

- visualizzare spese su mappa
- tracking posizione utente
- vedere dove sono state fatte le spese

API utilizzata:

```
OpenStreetMap
oppure
Google Maps SDK
```

---

# Grafici

Grafici disponibili:

- spesa per categoria
- spesa per utente
- spesa per periodo

Librerie possibili:

```
MPAndroidChart
oppure
Charts Compose
```

---

# Notifiche

Notifiche quando:

- qualcuno aggiunge una spesa
- qualcuno ti deve soldi
- viene chiuso un debito

Possibili tipi:

- push notification
- notifiche locali

---

# API esterne

Possibili API usate:

### Exchange rates API

Serve per convertire valute se il gruppo è internazionale.

Esempio:

```
https://api.exchangerate.host/latest
```

---

# Uso della fotocamera

La fotocamera verrà usata per:

- fotografare scontrini
- salvare immagine nello storage locale
- collegare lo scontrino alla spesa

Tecnologia:

```
CameraX
```

---

# Storage

Salvataggio:

- immagini scontrini
- foto profilo

Posizione:

```
External Storage
oppure
MediaStore
```

---

# Sicurezza

Funzionalità:

- login biometrico (impronta digitale / face unlock)
- autenticazione sicura
- accesso ai dati protetto

API:

```
BiometricPrompt
```

---

# Possibili estensioni future

- pagamenti integrati (Stripe / PayPal)
- AI per leggere scontrini
- sincronizzazione cloud
- versione web
- widget Android

---

# Obiettivo del progetto

Realizzare una app Android completa che dimostri l'utilizzo di:

- sensori
- mappe
- database
- networking
- UI moderne
- notifiche
- integrazione con sistema operativo

Il progetto mostra competenze avanzate nello sviluppo Android moderno.



---
