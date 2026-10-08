# DriveDesk: demo guide

## Run it

```powershell
cd C:\DriveDesk
.\run.ps1            # compiles, then serves http://localhost:8090/
.\run.ps1 -Reset     # same, but wipes data/ first so the demo data is freshly dated "today"
```

Needs Java 17+ (`java -version`). No other installs. Stop with Ctrl+C. Everything you do is saved to `data/*.csv` immediately, so a restart keeps it. The original console version still works: `java -cp out app.DonationDriveApp`.

**Before presenting:** run `.\run.ps1 -Reset` once, because the demo data is dated relative to the day it is first created and goes stale otherwise.

## 6-minute walkthrough

| Step | Do this | What it shows |
|---|---|---|
| 1. Overview | Open the site. Point at the flow chart, then hover a day. Switch Chart / Table. | Live stock, targets against the clock, expiry countdown, activity ledger |
| 2. Polymorphism | **Log donation**: click Food, Clothing, Medicine, Books in turn. | Each category creates a different subclass with its own fields. The line under the picker names the class |
| 3. Validation | Log **Food** with an expiry date in the past, or **Clothing** with condition *Damaged*. | `isUsable()` is overridden per subclass, so the item is rejected with a reason |
| 4. Logging a lot | Log *Medicine* "ORS sachets", 30, expiring in 3 days. | It appears in Inventory, in Expiring soon, and as a WARN entry in Activity |
| 5. Targets | **Needs**: click *Edit* on Rice and raise the target. | Collected is computed from inventory; the pace marker shows if the drive is behind |
| 6. Duplicate detection | **Beneficiaries**: register a family using phone `9460011001`. | `equals()` on phone number, `DuplicateBeneficiaryException`, and a DUP entry in Activity |
| 7. Fair distribution | **Distribution**, pick Clothing, hand over to the first family. They move to the Blocked list ("eligible again …"). | 30-day repeat rule enforced in `DistributionService` |
| 8. Earliest expiry first | Distribution, Medicine, quantity larger than the first lot (for example 35). | One request is split across lots, soonest-expiring first |
| 9. Stock check | Distribution, Books, quantity `99999`. | `InsufficientStockException`, and a BLOCK entry in Activity |
| 10. Reports | **Reports**: change the donor, press Copy. | `StringBuilder` reports, donor acknowledgement |
| 11. Persistence | Stop the server, run it again. | Everything is still there (CSV file I/O) |
| 12. Next drive | **Drive** tab: enter a name (e.g. "Spring Relief"), leave both boxes ticked, press the button, then confirm. | The old drive is archived under *Past drives* with its summary. Usable stock and targets carry over, donors and families stay, and the 30-day repeat rule still holds (families helped last week stay blocked) |

Step 12 changes the data, so do it last, or run `.\run.ps1 -Reset` afterwards to get the first drive back.

## Where each OOP idea lives

| Concept | Where |
|---|---|
| Abstraction / abstract classes | `model/item/DonationItem.java`, `model/person/Person.java` |
| Inheritance | `FoodItem`, `MedicineItem`, `ClothingItem`, `BookItem` extend `DonationItem`; `Donor`, `Beneficiary` extend `Person` |
| Polymorphism | `isUsable()` and `getDistributionPriority()` differ per subclass; `DistributionService.pickNextItem()` uses them without knowing the type. `carriedOver()` is another: each subclass copies its own fields into the next drive |
| Interfaces | `contract/Expirable.java` (only some items expire; has `default` methods), `contract/Persistable.java` (anything saved to CSV) |
| Method overloading | `InventoryService.addDonation(item)` and `addDonation(item, donor)`; `FoodItem` has three constructors |
| `equals` / `hashCode` | `Beneficiary`: same phone means the same family |
| Encapsulation | Private fields with validation in constructors and setters (`DonationItem`, `Beneficiary`) |
| Composition | `Drive` has donors, beneficiaries, inventory, needs, distributions and an activity log |
| Enums | `Category`, `Condition`, `ActivityType`, `Distribution.Status` |
| Custom exceptions | `exception/` package (three checked exceptions) |
| Static utility / `synchronized` | `util/IdGenerator.java` (shared counters), `util/AppConstants.java` |
| Records | `DistributionService.Suggestion`, `BlockedFamily`, `NeedsService.NeedStatus` |
| File I/O | `storage/FileManager.java` (CSV, atomic save through a temp file) |
| Closing a drive | `service/DriveService.java` (validation, what carries over, records for stats), `Drive.beginNext()` (clears one run, keeps the people), `FileManager.archive()` (copies the CSVs into `data/archive/<date>_<name>/`) |
| Layering | `service/` holds every rule. `web/ApiHandler.java` and `app/DonationDriveApp.java` only call services; the models never print |

## How the web part fits

`web/WebServer.java` (built-in `com.sun.net.httpserver`, no frameworks) serves `web/public/` and the JSON API under `/api`. `web/ApiHandler.java` turns requests into service calls and saves after every write. `web/Json.java` is a small hand-written JSON reader/writer. The page in `web/public/` only calls `/api`; none of the rules are in the browser.

## If something looks off

| Symptom | Fix |
|---|---|
| Port 8090 already in use | `.\run.ps1 9000` and open `http://localhost:9000/` |
| Old data or dates look stale | `.\run.ps1 -Reset` (this also deletes `data/archive/`, so past drives disappear) |
| The countdown says "ended" | The drive's days are used up. Use the Drive tab to start the next one, or `-Reset` for fresh demo data |
| Fonts look plain | The page loads Inter from Google Fonts; offline it falls back to the system font, which is fine |
