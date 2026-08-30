# Evacuee Property Register — Office of the Deputy Custodian, Baramulla

An offline record-keeping application for maintaining the register of evacuee
properties: property particulars, allotment and occupancy, rent and arrears,
litigation status, and printable office reports.

The whole application is one file: **`evacuee-property-register.html`**

## How to run it

Double-click `evacuee-property-register.html`. It opens in any browser
(Chrome, Edge, Firefox).

There is nothing to install, no internet connection needed, and no login.
Everything works on the office computer as it is.

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

## Where the data is kept — please read

Records are stored **inside the browser on that one computer**. Nothing is sent
anywhere, which keeps the data private, but it also means:

- Clearing the browser's data will erase the register.
- The records do not appear on any other computer by themselves.

**So take a backup at the end of each working day:** open **Backup & Data** →
**Download Backup File**, and keep the file on a pen drive or a separate folder.

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
