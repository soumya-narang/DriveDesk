package service;

import exception.DistributionNotAllowedException;
import exception.DuplicateBeneficiaryException;
import exception.InsufficientStockException;
import model.ActivityType;
import model.Category;
import model.Distribution;
import model.Drive;
import model.item.DonationItem;
import model.person.Beneficiary;
import util.AppConstants;
import util.IdGenerator;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class DistributionService {
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH);

    /** A family that cannot receive a category right now, and when they last did. */
    public record BlockedFamily(Beneficiary beneficiary, LocalDate lastReceived) {}

    /** What the Distribution screen shows after a category is picked. */
    public record Suggestion(DonationItem nextItem, int availableUnits,
                             List<Beneficiary> eligible, List<BlockedFamily> blocked) {}

    private final Drive drive;
    private final InventoryService inventory;

    public DistributionService(Drive drive, InventoryService inventory) {
        this.drive = drive;
        this.inventory = inventory;
    }

    public Beneficiary registerBeneficiary(String name, String phone, int familySize, String address, int priority)
            throws DuplicateBeneficiaryException {
        Beneficiary candidate = new Beneficiary(IdGenerator.nextBeneficiaryId(), name, phone, familySize, address, priority);
        for (Beneficiary existing : drive.getBeneficiaries()) {
            if (existing.equals(candidate)) {
                drive.log(ActivityType.DUP, candidate.getId() + " matches " + existing.getId() + " phone");
                throw new DuplicateBeneficiaryException(candidate.getId(), existing);
            }
        }
        drive.getBeneficiaries().add(candidate);
        return candidate;
    }

    /** Most recent handover of this category to this family, or null. */
    public LocalDate lastReceived(String beneficiaryId, Category category) {
        LocalDate last = null;
        for (Distribution d : drive.getDistributions()) {
            if (d.isHandedOver() && d.getBeneficiaryId().equals(beneficiaryId) && d.getCategory() == category
                    && (last == null || d.getDate().isAfter(last))) {
                last = d.getDate();
            }
        }
        return last;
    }

    private boolean withinRepeatWindow(LocalDate last) {
        return last != null && last.plusDays(AppConstants.REPEAT_WINDOW_DAYS).isAfter(LocalDate.now());
    }

    /** Polymorphism: works for any item type without knowing the subclass. */
    public DonationItem pickNextItem(Category category) {
        DonationItem best = null;
        for (DonationItem item : inventory.getUsableStock(category)) {
            if (best == null || item.getDistributionPriority() < best.getDistributionPriority()) best = item;
        }
        return best;
    }

    public Suggestion suggest(Category category) {
        List<Beneficiary> eligible = new ArrayList<>();
        List<BlockedFamily> blocked = new ArrayList<>();
        for (Beneficiary b : drive.getBeneficiaries()) {
            LocalDate last = lastReceived(b.getId(), category);
            if (withinRepeatWindow(last)) blocked.add(new BlockedFamily(b, last));
            else eligible.add(b);
        }
        eligible.sort(Comparator.comparingInt(Beneficiary::getPriorityLevel)
                .thenComparing(Comparator.comparingInt(Beneficiary::getFamilySize).reversed()));
        int units = 0;
        for (DonationItem i : inventory.getUsableStock(category)) units += i.getQuantity();
        return new Suggestion(pickNextItem(category), units, eligible, blocked);
    }

    /**
     * Hands items to a family, earliest-expiring stock first, spilling over to the next lot if needed.
     * @param itemId optional: force a specific lot instead of the suggested one
     */
    public List<Distribution> distribute(String beneficiaryId, Category category, int quantity, String itemId)
            throws DistributionNotAllowedException, InsufficientStockException {
        Beneficiary b = drive.findBeneficiary(beneficiaryId);
        if (b == null) throw new IllegalArgumentException("Unknown beneficiary: " + beneficiaryId);
        if (quantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");

        LocalDate last = lastReceived(b.getId(), category);
        if (withinRepeatWindow(last)) {
            String reason = "repeat within " + AppConstants.REPEAT_WINDOW_DAYS + " days (got " + last.format(SHORT_DATE) + ")";
            block(b, category, quantity, reason,
                    b.getId() + " already received " + category + " on " + last + " (within "
                            + AppConstants.REPEAT_WINDOW_DAYS + " days)");
            throw new DistributionNotAllowedException(b.getId() + " already received " + category + " on " + last
                    + " (within " + AppConstants.REPEAT_WINDOW_DAYS + " days)");
        }

        List<DonationItem> lots = new ArrayList<>(inventory.getUsableStock(category));
        if (itemId != null && !itemId.isBlank()) {
            lots.removeIf(i -> !i.getItemId().equals(itemId));
            if (lots.isEmpty()) throw new IllegalArgumentException(itemId + " is not an available " + category + " lot");
        }
        lots.sort(Comparator.comparingInt(DonationItem::getDistributionPriority)
                .thenComparing(DonationItem::getReceivedOn));

        int available = 0;
        for (DonationItem lot : lots) available += lot.getQuantity();
        if (available < quantity) {
            String msg = "Only " + available + " " + category + " units in stock, " + quantity + " requested";
            block(b, category, quantity, "only " + available + " in stock", b.getId() + " " + msg);
            throw new InsufficientStockException(category.getLabel(), available, quantity);
        }

        List<Distribution> made = new ArrayList<>();
        int left = quantity;
        for (DonationItem lot : lots) {
            if (left == 0) break;
            int take = Math.min(left, lot.getQuantity());
            lot.reduceQuantity(take);
            left -= take;
            Distribution d = new Distribution(IdGenerator.nextDistributionId(), b.getId(), lot.getItemId(), category,
                    lot.getName(), take, LocalDate.now(), Distribution.Status.HANDED_OVER, "");
            drive.getDistributions().add(d);
            made.add(d);
            drive.log(ActivityType.OUT, b.getId() + " " + b.getName() + " received " + take + " x " + lot.getName()
                    + " (" + lot.getItemId() + ")");
        }
        return made;
    }

    private void block(Beneficiary b, Category category, int quantity, String note, String logText) {
        drive.getDistributions().add(new Distribution(IdGenerator.nextDistributionId(), b.getId(), "", category, "",
                quantity, LocalDate.now(), Distribution.Status.BLOCKED, note));
        drive.log(ActivityType.BLOCK, logText);
    }
}
