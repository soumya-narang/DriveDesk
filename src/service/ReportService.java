package service;

import contract.Expirable;
import model.Category;
import model.Distribution;
import model.Drive;
import model.item.DonationItem;
import model.person.Beneficiary;
import model.person.Donor;
import util.AppConstants;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/** Builds plain-text reports with StringBuilder. */
public class ReportService {
    private final Drive drive;
    private final InventoryService inventory;
    private final NeedsService needs;

    public ReportService(Drive drive, InventoryService inventory, NeedsService needs) {
        this.drive = drive;
        this.inventory = inventory;
        this.needs = needs;
    }

    public String driveSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("DRIVE SUMMARY: ").append(drive.getName()).append('\n');
        sb.append("Location : ").append(drive.getLocation()).append('\n');
        sb.append("Day      : ").append(drive.getCurrentDay()).append(" of ").append(drive.getDurationDays())
                .append(drive.isOver() ? " (ended " + drive.getEndDate() + ")" : "").append('\n');
        sb.append("Generated: ").append(LocalDate.now()).append("\n\n");

        sb.append("Donors registered     : ").append(drive.getDonors().size()).append('\n');
        sb.append("Families registered   : ").append(drive.getBeneficiaries().size()).append('\n');
        Set<String> served = new HashSet<>();
        int handedOut = 0;
        for (Distribution d : drive.getDistributions()) {
            if (d.isHandedOver() && drive.isInDrive(d.getDate())) {   // records carried over from the last drive are not this drive's
                served.add(d.getBeneficiaryId());
                handedOut += d.getQuantity();
            }
        }
        sb.append("Families served       : ").append(served.size()).append('\n');
        sb.append("Units handed out      : ").append(handedOut).append('\n');
        sb.append("Units in stock (usable): ").append(inventory.totalUnitsInStock()).append("\n\n");

        sb.append("STOCK BY CATEGORY\n");
        for (Category c : Category.values()) {
            int units = 0;
            for (DonationItem i : inventory.getUsableStock(c)) units += i.getQuantity();
            sb.append(String.format("  %-10s %5d units%n", c.getLabel(), units));
        }

        sb.append("\nTARGETS\n");
        if (drive.getNeeds().isEmpty()) sb.append("  No targets set.\n");
        for (NeedsService.NeedStatus s : needs.getStillNeeded()) {
            sb.append(String.format("  %-22s %4d / %-4d  %s%n", s.need().getItemName(), s.collected(),
                    s.need().getTargetQuantity(), s.isMet() ? "target met" : "need " + s.remaining() + " more"));
        }

        sb.append("\nEXPIRING WITHIN ").append(AppConstants.NEAR_EXPIRY_DAYS).append(" DAYS\n");
        var expiring = inventory.getExpiringSoon(AppConstants.NEAR_EXPIRY_DAYS);
        if (expiring.isEmpty()) sb.append("  Nothing expiring soon.\n");
        for (DonationItem i : expiring) {
            sb.append(String.format("  %-5s %-22s x%-4d %d days%n", i.getItemId(), i.getName(), i.getQuantity(),
                    ((Expirable) i).daysToExpiry()));
        }
        var expired = inventory.getExpiredStock();
        if (!expired.isEmpty()) {
            sb.append("\nEXPIRED IN STORAGE (do not distribute)\n");
            for (DonationItem i : expired) {
                sb.append(String.format("  %-5s %-22s x%-4d%n", i.getItemId(), i.getName(), i.getQuantity()));
            }
        }
        return sb.toString();
    }

    public String donorAcknowledgement(String donorId) {
        Donor donor = drive.findDonor(donorId);
        if (donor == null) throw new IllegalArgumentException("Unknown donor: " + donorId);

        StringBuilder sb = new StringBuilder();
        sb.append("Date: ").append(LocalDate.now()).append("\n\n");
        sb.append("Dear ").append(donor.getName()).append(",\n\n");
        sb.append("Thank you for supporting the ").append(drive.getName()).append(" drive in ")
                .append(drive.getLocation()).append(". Here is what you gave and where it went:\n\n");

        int given = 0, used = 0;
        for (DonationItem i : drive.getInventory()) {
            if (!i.getDonorId().equals(donor.getId())) continue;
            int out = i.getInitialQuantity() - i.getQuantity();
            given += i.getInitialQuantity();
            used += out;
            sb.append(String.format("  %-5s %-24s %4d received, %4d handed to families%n",
                    i.getItemId(), i.getName(), i.getInitialQuantity(), out));
        }
        if (given == 0) sb.append("  (no donations recorded yet)\n");

        Set<String> families = new HashSet<>();
        for (Distribution d : drive.getDistributions()) {
            DonationItem item = drive.findItem(d.getItemId());
            if (d.isHandedOver() && item != null && item.getDonorId().equals(donor.getId())) {
                Beneficiary b = drive.findBeneficiary(d.getBeneficiaryId());
                if (b != null) families.add(b.getId());
            }
        }
        sb.append("\nTotal donated: ").append(given).append(" units. Handed over so far: ").append(used)
                .append(" units to ").append(families.size()).append(" ").append(families.size() == 1 ? "family" : "families").append(".\n\n");
        sb.append("With gratitude,\n").append(drive.getName()).append(" organising team\n");
        return sb.toString();
    }
}
