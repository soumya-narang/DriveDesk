package service;

import model.Category;
import model.Drive;
import model.Need;
import model.item.DonationItem;

import java.util.ArrayList;
import java.util.List;

public class NeedsService {
    /** A need together with how much has been collected so far. */
    public record NeedStatus(Need need, int collected) {
        public int remaining() { return Math.max(0, need.getTargetQuantity() - collected); }
        public boolean isMet() { return collected >= need.getTargetQuantity(); }
    }

    private final Drive drive;

    public NeedsService(Drive drive) { this.drive = drive; }

    /** Adds a target, or updates it if the same category + item already has one. */
    public Need addNeed(Category category, String itemName, int target) {
        for (Need n : drive.getNeeds()) {
            if (n.getCategory() == category && n.getItemName().equalsIgnoreCase(itemName.trim())) {
                n.setTargetQuantity(target);
                return n;
            }
        }
        Need need = new Need(category, itemName, target);
        drive.getNeeds().add(need);
        return need;
    }

    /** Everything ever received that counts toward the need (distributed units still count as collected). */
    public int collected(Need need) {
        int total = 0;
        for (DonationItem i : drive.getInventory()) {
            if (need.matches(i.getCategory(), i.getName())) total += i.getInitialQuantity();
        }
        return total;
    }

    public List<NeedStatus> getStillNeeded() {
        List<NeedStatus> out = new ArrayList<>();
        for (Need n : drive.getNeeds()) out.add(new NeedStatus(n, collected(n)));
        return out;
    }
}
