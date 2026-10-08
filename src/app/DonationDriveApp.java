package app;

import contract.Expirable;
import exception.DistributionNotAllowedException;
import exception.DuplicateBeneficiaryException;
import exception.InsufficientStockException;
import model.Category;
import model.Condition;
import model.Drive;
import model.item.BookItem;
import model.item.ClothingItem;
import model.item.DonationItem;
import model.item.FoodItem;
import model.item.MedicineItem;
import model.person.Donor;
import service.DistributionService;
import service.InventoryService;
import service.NeedsService;
import service.ReportService;
import storage.FileManager;
import util.AppConstants;
import util.IdGenerator;

import java.time.LocalDate;
import java.util.Scanner;

/** Console menu. It reads input and prints output; all rules live in the services. */
public class DonationDriveApp {
    private final Scanner in = new Scanner(System.in);
    private final Drive drive;
    private final FileManager files = new FileManager(AppConstants.DATA_DIR);
    private final InventoryService inventory;
    private final NeedsService needs;
    private final DistributionService distribution;
    private final ReportService reports;

    DonationDriveApp(Drive drive) {
        this.drive = drive;
        this.inventory = new InventoryService(drive);
        this.needs = new NeedsService(drive);
        this.distribution = new DistributionService(drive, inventory);
        this.reports = new ReportService(drive, inventory, needs);
    }

    public static void main(String[] args) throws Exception {
        FileManager fm = new FileManager(AppConstants.DATA_DIR);
        Drive drive = fm.hasData() ? fm.loadAll() : new Drive("My Donation Drive", "Your city", LocalDate.now(), 30);
        new DonationDriveApp(drive).run();
    }

    private void run() throws Exception {
        while (true) {
            System.out.println("\n===== DONATION DRIVE MANAGER =====");
            System.out.println(" 1. Register donor\n 2. Log a donation\n 3. Set a need / target\n 4. View inventory");
            System.out.println(" 5. View \"Still Needed\" list\n 6. View items expiring soon\n 7. Register beneficiary");
            System.out.println(" 8. Distribute items\n 9. Reports\n10. Save & exit\n==================================");
            String choice = ask("> ");
            try {
                switch (choice) {
                    case "1" -> registerDonor();
                    case "2" -> logDonation();
                    case "3" -> setNeed();
                    case "4" -> drive.getInventory().forEach(System.out::println);
                    case "5" -> stillNeeded();
                    case "6" -> expiring();
                    case "7" -> registerBeneficiary();
                    case "8" -> distribute();
                    case "9" -> reports();
                    case "10" -> {
                        files.saveAll(drive);
                        System.out.println("Saved. Goodbye.");
                        return;
                    }
                    default -> System.out.println("Choose 1-10.");
                }
            } catch (DuplicateBeneficiaryException | DistributionNotAllowedException | InsufficientStockException e) {
                System.out.println("✘ Not allowed: " + e.getMessage());
            } catch (RuntimeException e) {
                System.out.println("✘ " + e.getMessage());
            }
        }
    }

    private String ask(String prompt) {
        System.out.print(prompt);
        return in.nextLine().trim();
    }

    private void registerDonor() {
        Donor d = new Donor(IdGenerator.nextDonorId(), ask("Name: "), ask("Phone: "), ask("Organisation? (y/n): ").equalsIgnoreCase("y"));
        drive.getDonors().add(d);
        System.out.println("Registered " + d);
    }

    private void logDonation() {
        Donor donor = drive.findDonor(ask("Donor ID: "));
        if (donor == null) throw new IllegalArgumentException("Unknown donor");
        Category cat = Category.parse(ask("Category (FOOD/CLOTHING/MEDICINE/BOOK): "));
        String name = ask("Item name: ");
        int qty = Integer.parseInt(ask("Quantity: "));
        DonationItem item = switch (cat) {
            case FOOD -> new FoodItem(name, qty, donor.getId(), LocalDate.parse(ask("Expiry (yyyy-mm-dd): ")));
            case MEDICINE -> new MedicineItem(name, qty, donor.getId(), LocalDate.parse(ask("Expiry (yyyy-mm-dd): ")),
                    ask("Sealed? (y/n): ").equalsIgnoreCase("y"));
            case CLOTHING -> new ClothingItem(name, qty, donor.getId(), ask("Size: "), ask("Season: "), Condition.parse(ask("Condition: ")));
            case BOOK -> new BookItem(name, qty, donor.getId(), ask("Subject: "), ask("Grade: "), Condition.parse(ask("Condition: ")));
        };
        inventory.addDonation(item, donor);
        System.out.println("Logged " + item);
    }

    private void setNeed() {
        needs.addNeed(Category.parse(ask("Category: ")), ask("Item name: "), Integer.parseInt(ask("Target quantity: ")));
        System.out.println("Target saved.");
    }

    private void stillNeeded() {
        System.out.println("--- Still Needed ---");
        for (NeedsService.NeedStatus s : needs.getStillNeeded()) {
            System.out.printf("%-14s collected %d / %d   %s%n", s.need().getItemName(), s.collected(),
                    s.need().getTargetQuantity(), s.isMet() ? "target met" : "need " + s.remaining() + " more");
        }
    }

    private void expiring() {
        System.out.println("--- Expiring within " + AppConstants.NEAR_EXPIRY_DAYS + " days ---");
        for (DonationItem i : inventory.getExpiringSoon(AppConstants.NEAR_EXPIRY_DAYS)) {
            System.out.printf("%-10s %s  %-18s x%-4d expires in %d days%n", "[" + i.getCategory() + "]", i.getItemId(),
                    i.getName(), i.getQuantity(), ((Expirable) i).daysToExpiry());
        }
    }

    private void registerBeneficiary() throws DuplicateBeneficiaryException {
        var b = distribution.registerBeneficiary(ask("Name: "), ask("Phone: "), Integer.parseInt(ask("Family size: ")),
                ask("Address: "), Integer.parseInt(ask("Priority (1-3): ")));
        System.out.println("Registered " + b);
    }

    private void distribute() throws DistributionNotAllowedException, InsufficientStockException {
        String id = ask("Beneficiary ID: ");
        Category cat = Category.parse(ask("Category: "));
        int qty = Integer.parseInt(ask("Quantity: "));
        distribution.distribute(id, cat, qty, null).forEach(d -> System.out.println("Handed over: " + d));
    }

    private void reports() {
        System.out.println(reports.driveSummary());
        String donor = ask("Donor ID for an acknowledgement (blank to skip): ");
        if (!donor.isEmpty()) System.out.println(reports.donorAcknowledgement(donor));
    }
}
