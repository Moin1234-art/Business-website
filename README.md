# Evacuee Property Register — Office of the Deputy Custodian, Baramulla

An offline record-keeping application for maintaining the register of evacuee
properties: property particulars, allotment and occupancy, rent and arrears,
litigation status, and printable office reports.

The register itself is one self-contained file,
**`evacuee-property-register.html`**, which also ships as an Android app.

## Three ways to run it

**1. On the office computer.** Double-click `evacuee-property-register.html`.
It opens in any browser (Chrome, Edge, Firefox). Nothing to install, no
internet, no login.

**2. As an Android app.** Install `android/dist/Evacuee-Register.apk` on a
phone or tablet — see *Installing the Android app* below.

**3. From a web link.** If GitHub Pages is enabled for this repository, the
register is served at the project's Pages URL. Records still stay on whichever
device opens it; nothing is stored on the website.

All three run the same register. The app and the website are built from the
same `evacuee-property-register.html`, so there is only ever one version to
maintain.

## Installing the Android app

The APK is not on the Play Store, so the phone must be allowed to install it
directly:

1. Copy `Evacuee-Register.apk` to the phone (cable, email to himself, or a
   pen drive).
2. Open it with the phone's Files app and tap **Install**.
3. Android will warn that the app is from an unknown source — choose
   **Settings**, allow installation from that app (usually Files or Chrome),
   then go back and tap **Install** again.
4. "Evacuee Register" appears in the app drawer.

**What the app asks for: nothing.** It requests no permissions at all — no
contacts, no location, no storage, and no internet. Backups are written through
Android's own file picker, which is why no storage permission is needed.

Minimum Android 5.0. Tested to install on Android 14 and 15.

### Inside the app

- **Backup** opens Android's "save file" screen, so the clerk chooses where the
  backup goes — phone storage, an SD card, or Google Drive if he uses it.
- **Restore** opens the file picker to choose a backup file.
- **Print / Save as PDF** uses Android's print service. Choosing "Save as PDF"
  produces the same register report as the desktop version.
- **Back** closes an open property form first, so a half-typed entry is never
  lost by accident; pressing it again asks before closing the app.

### Rebuilding the app

    ./android/build.sh

Needs a Debian/Ubuntu machine with `android-sdk-platform-23`,
`android-sdk-build-tools`, `apksigner`, a JDK and `dx` (see the header of
`build.sh` for exact packages). The script refreshes the bundled register from
`evacuee-property-register.html`, so edit that one file and rebuild.

> **Keep the signing key.** The first build creates `android/evacuee-register.jks`.
> It is deliberately not committed. If it is lost, a rebuilt app can still be
> installed, but only after uninstalling the old one first — which would erase
> the records on that device unless a backup was taken. Store it somewhere safe.

## What it does

| Section | Purpose |
| --- | --- |
| **Dashboard** | Totals at a glance — properties, allotted / vacant / litigation / encroached, total area, annual rent demand, arrears outstanding, plus breakdowns by status and tehsil and an "Attention Required" list. |
| **Property Register** | The main register. Add, edit, delete, search, filter by tehsil / status / nature, and sort by any column. |
| **Reports** | Generate a printable register — filtered by tehsil and status — with office heading, totals and signature lines. Print or save as PDF. |
| **Backup & Data** | Download a backup file, export to Excel/CSV, restore from a backup, load sample records, clear the register. |
| **Help** | Plain-language instructions inside the app itself. |

## Details recorded per property

- **Identification** — auto-allotted Property ID (`EP/BLA/0001`…), khasra/survey no., khata/khewat no., office file no.
- **Location** — village/mohalla, tehsil, niabat/patwar halqa
- **Property** — nature (house, shop, agricultural land, orchard, plot, godown, building), area in kanal and marla, original owner (evacuee)
- **Occupancy** — status, occupant/allottee, father's/husband's name, address, contact, date of allotment/lease, lease validity
- **Rent** — annual rent, rent paid up to, arrears outstanding
- **Legal** — court/case reference, remarks

Only village, tehsil and nature of property are compulsory. Everything else can
be filled in later as records come to hand.

## Cloud sync

Two devices — the office computer and the phone — can be kept showing the same
register, without any server, account, subscription or API key.

Sync works through **one JSON file** kept in whatever cloud folder the office
already uses: Google Drive, OneDrive, Dropbox, a shared network folder, even a
pen drive. Each device reads that file, merges it with its own records, and
writes the result back. The cloud app that owns the folder does all the
networking, which is why the Android app still requests **no INTERNET
permission**.

**Setting it up.** On the first device: *Backup & Data → Cloud Sync → Create
Sync File*, saved inside the cloud folder. On every other device: *Use Existing
Sync File*, and pick that same file. After that it syncs a few seconds after any
change, and whenever the register is opened.

**How conflicts are settled**

| Situation | Result |
| --- | --- |
| Different properties added on each device | Both are kept |
| Same property edited on both | The more recent edit wins |
| Property deleted on one device | Deleted on the other too — it does not come back |
| Both devices give a new property the same ID | One is renumbered automatically; both devices agree on the outcome |
| Sync file caught mid-upload / unreadable | Sync is refused and reported; local records are untouched |

The sync file has the same format as a backup file, so either can stand in for
the other.

Requires Chrome or Edge on the computer, or the Android app. Other browsers fall
back to manual Backup and Restore, which work everywhere.

## Deleted records

Deleting a property moves it to **Deleted Records** under Backup & Data, where it
can be restored. That is also what carries the deletion to the other device, so a
deleted property does not reappear at the next sync. *Remove Permanently* clears
them for good — but a copy still held on another device can then return.

## Where the data is kept — please read

Records are stored **on the device itself** — in the browser's storage on a
computer, and inside the app's private storage on Android
(`/data/data/com.baramulla.evacueeregister/…`, readable by no other app). Roughly
9,500 properties fit before the storage limit is reached.

Nothing is sent anywhere except the sync file, if sync is switched on. But it
also means:

- Clearing the browser's or the app's data erases the register.
- Uninstalling the Android app erases its copy.

**So take a backup at the end of each working day:** open **Backup & Data** →
**Download Backup File**, and keep the file on a pen drive or a separate folder.
Sync is not a backup — a record deleted on one device is deleted on the other.

To move the register to another computer, copy `evacuee-property-register.html`
and the backup file across, open the HTML file there, and use
**Restore from File**.

## Trying it out first

**Backup & Data → Load Sample Records** adds six specimen entries so the
register can be demonstrated and practised on. **Delete All Records** clears
them before real data entry begins.

## Notes

- Area follows the local measure: 20 marla = 1 kanal. Enter whole kanals in the
  kanal box and 0–19 in the marla box.
- Amounts are in rupees and shown in the Indian numbering format.
- The CSV export opens directly in Excel.
- The app runs entirely offline — it loads no fonts, scripts or styles from the
  internet, so it works exactly the same with the network disconnected.
