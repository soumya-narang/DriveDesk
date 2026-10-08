package service;

import contract.Expirable;
import model.ActivityType;
import model.Category;
import model.Drive;
import model.item.DonationItem;
import model.person.Donor;
import util.AppConstants;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class InventoryService {
    private final Drive drive;

    public InventoryService(Drive drive) { this.drive = drive; }

    /** Overload 1: donation whose donor is already recorded on the item. */
    public void addDonation(DonationItem item) {
        if (!item.isUsable()) {
            throw new IllegalArgumentException(item.getName() + " cannot be accepted: it is expired, damaged or unsealed");
        }
        drive.getInventory().add(item);
        drive.log(ActivityType.IN, item.getItemId() + " " + item.getName() + " x" + item.getQuantity() + " received");
        if (item instanceof Expirable e && e.isNearExpiry(AppConstants.NEAR_EXPIRY_DAYS)) {
            drive.log(ActivityType.WARN, item.getItemId() + " " + item.getName() + " expires in " + e.daysToExpiry() + " days");
        }
    }

    /** Overload 2: also links the donation to the donor. */
    public void addDonation(DonationItem item, Donor donor) {
        if (!item.isUsable()) {
            throw new IllegalArgumentException(item.getName() + " cannot be accepted: it is expired, damaged or unsealed");
        }
        item.setDonorId(donor.getId());
        donor.addDonatedItemId(item.getItemId());
        addDonation(item);
    }

    public List<DonationItem> getUsableStock() {
        List<DonationItem> out = new ArrayList<>();
        for (DonationItem i : drive.getInventory()) {
            if (i.isUsable() && i.getQuantity() > 0) out.add(i);
        }
        return out;
    }

    public List<DonationItem> getUsableStock(Category category) {
        List<DonationItem> out = new ArrayList<>();
        for (DonationItem i : getUsableStock()) {
            if (i.getCategory() == category) out.add(i);
        }
        return out;
    }

    /** Expirable lots in stock expiring within the given number of days (soonest first). */
    public List<DonationItem> getExpiringSoon(int withinDays) {
        List<DonationItem> out = new ArrayList<>();
        for (DonationItem i : getUsableStock()) {
            if (i instanceof Expirable e && e.isNearExpiry(withinDays)) out.add(i);
        }
        out.sort(Comparator.comparingLong(i -> ((Expirable) i).daysToExpiry()));
        return out;
    }

    /** Lots that expired while in storage and still hold stock. */
    public List<DonationItem> getExpiredStock() {
        List<DonationItem> out = new ArrayList<>();
        for (DonationItem i : drive.getInventory()) {
            if (i instanceof Expirable e && e.daysToExpiry() < 0 && i.getQuantity() > 0) out.add(i);
        }
        return out;
    }

    public int totalUnitsInStock() {
        int total = 0;
        for (DonationItem i : getUsableStock()) total += i.getQuantity();
        return total;
    }
}
