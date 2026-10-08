package app;

import model.ActivityType;
import model.Category;
import model.Condition;
import model.Distribution;
import model.Drive;
import model.Need;
import model.item.BookItem;
import model.item.ClothingItem;
import model.item.DonationItem;
import model.item.FoodItem;
import model.item.MedicineItem;
import model.person.Beneficiary;
import model.person.Donor;
import util.IdGenerator;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Sample data for a first run, so the dashboard has something to show. Dates are relative to today. */
public final class DemoData {
    private DemoData() {}

    public static Drive create() {
        LocalDate today = LocalDate.now();
        Drive drive = new Drive("Winter Relief", "Jaipur", today.minusDays(17), 30);

        Donor rotary = donor(drive, "Rotary Club Jaipur", "9414000111", true);
        Donor meena = donor(drive, "Meena Kapoor", "9829011222", false);
        Donor nss = donor(drive, "MUJ NSS Unit", "9001233344", true);
        Donor rahul = donor(drive, "Rahul Sharma", "9772055566", false);

        // Food
        item(drive, rotary, new FoodItem(IdGenerator.nextItemId(), "Rice 5kg", 300, 300, rotary.getId(), today.minusDays(15), today.plusDays(120), false), 15);
        item(drive, meena, new FoodItem(IdGenerator.nextItemId(), "Rice 1kg", 150, 150, meena.getId(), today.minusDays(12), today.plusDays(95), false), 12);
        item(drive, nss, new FoodItem(IdGenerator.nextItemId(), "Bread packets", 30, 30, nss.getId(), today.minusDays(2), today.plusDays(2), true), 2);
        item(drive, rahul, new FoodItem(IdGenerator.nextItemId(), "Biscuit packets", 60, 60, rahul.getId(), today.minusDays(4), today.plusDays(5), false), 4);
        item(drive, rotary, new FoodItem(IdGenerator.nextItemId(), "Dal 1kg", 120, 120, rotary.getId(), today.minusDays(9), today.plusDays(150), false), 9);
        // Clothing
        item(drive, meena, new ClothingItem(IdGenerator.nextItemId(), "Blanket", 180, 180, meena.getId(), today.minusDays(14), "Free", "Winter", Condition.NEW), 14);
        item(drive, nss, new ClothingItem(IdGenerator.nextItemId(), "Sweater", 70, 70, nss.getId(), today.minusDays(8), "M", "Winter", Condition.GOOD), 8);
        item(drive, rahul, new ClothingItem(IdGenerator.nextItemId(), "Jacket", 40, 40, rahul.getId(), today.minusDays(6), "L", "Winter", Condition.GOOD), 6);
        // Medicine
        item(drive, rotary, new MedicineItem(IdGenerator.nextItemId(), "Paracetamol strips", 80, 80, rotary.getId(), today.minusDays(10), today.plusDays(6), true), 10);
        item(drive, nss, new MedicineItem(IdGenerator.nextItemId(), "ORS sachets", 120, 120, nss.getId(), today.minusDays(7), today.plusDays(240), true), 7);
        item(drive, meena, new MedicineItem(IdGenerator.nextItemId(), "Cough syrup", 30, 30, meena.getId(), today.minusDays(5), today.plusDays(3), true), 5);
        // Books
        item(drive, nss, new BookItem(IdGenerator.nextItemId(), "Notebooks", 90, 90, nss.getId(), today.minusDays(11), "Stationery", "Any", Condition.NEW), 11);
        item(drive, rahul, new BookItem(IdGenerator.nextItemId(), "NCERT Science", 35, 35, rahul.getId(), today.minusDays(3), "Science", "8", Condition.GOOD), 3);

        // Targets
        drive.getNeeds().add(new Need(Category.CLOTHING, "Blanket", 200));
        drive.getNeeds().add(new Need(Category.FOOD, "Rice", 400));
        drive.getNeeds().add(new Need(Category.BOOK, "Notebook", 300));
        drive.getNeeds().add(new Need(Category.MEDICINE, "ORS", 150));
        drive.getNeeds().add(new Need(Category.CLOTHING, "Sweater", 100));

        // Families
        String[][] fam = {
                {"Meena Devi", "9460011001", "5", "Shanti Nagar", "1"},
                {"Lakhan Singh", "9460011002", "4", "Ram Colony", "2"},
                {"Sunita Bai", "9460011003", "6", "Mansarovar slum", "1"},
                {"Farida Khan", "9460011004", "3", "Ghat Gate", "2"},
                {"Ramesh Meena", "9460011005", "7", "Jhotwara", "1"},
                {"Kavita Joshi", "9460011006", "4", "Sanganer", "3"},
                {"Imran Ali", "9460011007", "5", "Ramganj", "2"},
                {"Geeta Prajapat", "9460011008", "2", "Vidhyadhar Nagar", "2"},
                {"Mohan Lal", "9460011009", "6", "Bani Park", "1"},
                {"Pooja Saini", "9460011010", "3", "Malviya Nagar", "3"},
        };
        List<Beneficiary> families = new ArrayList<>();
        for (String[] f : fam) {
            Beneficiary b = new Beneficiary(IdGenerator.nextBeneficiaryId(), f[0], f[1], Integer.parseInt(f[2]), f[3], Integer.parseInt(f[4]));
            drive.getBeneficiaries().add(b);
            families.add(b);
        }

        // Past handovers: each family gets each category at most once, spread over the last 14 days
        Category[] cats = Category.values();
        for (int c = 0; c < cats.length; c++) {
            List<DonationItem> lots = new ArrayList<>();
            for (DonationItem i : drive.getInventory()) if (i.getCategory() == cats[c]) lots.add(i);
            for (int f = 0; f < families.size(); f++) {
                if ((f + c * 2) % 3 != 0) continue;
                DonationItem lot = lots.get((f + c) % lots.size());
                int qty = Math.min(lot.getQuantity(), 1 + (f * 3 + c) % 5);
                int daysAgo = (f * 3 + c * 5) % 14;
                handOver(drive, families.get(f), lot, qty, today.minusDays(daysAgo));
            }
        }

        // One blocked attempt, yesterday
        Beneficiary repeat = families.get(0);
        LocalDate lastFood = null;
        for (Distribution d : drive.getDistributions()) {
            if (d.getBeneficiaryId().equals(repeat.getId()) && d.getCategory() == Category.FOOD) lastFood = d.getDate();
        }
        if (lastFood != null) {
            String note = "repeat within 30 days (got " + lastFood.format(java.time.format.DateTimeFormatter.ofPattern("dd MMM", java.util.Locale.ENGLISH)) + ")";
            drive.getDistributions().add(new Distribution(IdGenerator.nextDistributionId(), repeat.getId(), "", Category.FOOD, "",
                    2, today.minusDays(1), Distribution.Status.BLOCKED, note));
            drive.log(ActivityType.BLOCK, repeat.getId() + " already received FOOD on " + lastFood + " (within 30 days)",
                    today.minusDays(1).atTime(16, 42));
        }

        drive.log(ActivityType.DUP, "B011 matches B002 phone", today.minusDays(1).atTime(11, 5));
        drive.log(ActivityType.WARN, "I003 Bread packets expires in 2 days", LocalDateTime.of(today, LocalTime.of(8, 0)));
        drive.getActivity().sort((a, b) -> a.getTime().compareTo(b.getTime()));
        return drive;
    }

    private static Donor donor(Drive drive, String name, String phone, boolean org) {
        Donor d = new Donor(IdGenerator.nextDonorId(), name, phone, org);
        drive.getDonors().add(d);
        return d;
    }

    private static void item(Drive drive, Donor donor, DonationItem item, int daysAgo) {
        drive.getInventory().add(item);
        donor.addDonatedItemId(item.getItemId());
        drive.log(ActivityType.IN, item.getItemId() + " " + item.getName() + " x" + item.getInitialQuantity() + " received",
                LocalDate.now().minusDays(daysAgo).atTime(10, 15));
    }

    private static void handOver(Drive drive, Beneficiary b, DonationItem lot, int qty, LocalDate date) {
        try {
            lot.reduceQuantity(qty);
        } catch (exception.InsufficientStockException e) {
            return;
        }
        drive.getDistributions().add(new Distribution(IdGenerator.nextDistributionId(), b.getId(), lot.getItemId(),
                lot.getCategory(), lot.getName(), qty, date, Distribution.Status.HANDED_OVER, ""));
        drive.log(ActivityType.OUT, b.getId() + " " + b.getName() + " received " + qty + " x " + lot.getName() + " (" + lot.getItemId() + ")",
                date.atTime(12, 30));
    }
}
