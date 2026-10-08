package service;

import model.ActivityType;
import model.Distribution;
import model.Drive;
import model.Need;
import model.item.DonationItem;
import model.person.Donor;
import util.AppConstants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Closing one drive and starting the next. The rules live here; storage and the web layer only call it. */
public class DriveService {
    public static final int MAX_DAYS = 365;

    /** What the organiser asks for when starting the next drive. */
    public record NextDrive(String name, String location, LocalDate startDate, int durationDays,
                            boolean carryStock, boolean keepTargets) {}

    /** What actually moved into the new drive. */
    public record Handover(String previousName, int lotsCarried, int unitsCarried, int lotsLeftBehind,
                           int unitsLeftBehind, int targetsKept, int handoversKept) {}

    /** What starting a drive would move, shown before the organiser confirms. */
    public record Preview(int usableLots, int usableUnits, int unusableLots, int unusableUnits,
                          int targets, int recentHandovers) {}

    /** Headline numbers for one drive's own run (lots received during it, not lots carried in). */
    public record Stats(int lots, int unitsReceived, int unitsHandedOut, int familiesServed) {}

    private final Drive drive;

    public DriveService(Drive drive) {
        this.drive = drive;
    }

    /** Counts only what happened on or after the drive's start date, so carried-over records do not inflate it. */
    public static Stats statsOf(Drive d) {
        int lots = 0, received = 0, handedOut = 0;
        for (DonationItem i : d.getInventory()) {
            if (d.isInDrive(i.getReceivedOn())) {
                lots++;
                received += i.getInitialQuantity();
            }
        }
        Set<String> served = new HashSet<>();
        for (Distribution x : d.getDistributions()) {
            if (x.isHandedOver() && d.isInDrive(x.getDate())) {
                handedOut += x.getQuantity();
                served.add(x.getBeneficiaryId());
            }
        }
        return new Stats(lots, received, handedOut, served.size());
    }

    public Preview preview() {
        int usableLots = 0, usableUnits = 0, unusableLots = 0, unusableUnits = 0;
        for (DonationItem i : drive.getInventory()) {
            if (i.getQuantity() == 0) continue;
            if (i.isUsable()) {
                usableLots++;
                usableUnits += i.getQuantity();
            } else {
                unusableLots++;
                unusableUnits += i.getQuantity();
            }
        }
        return new Preview(usableLots, usableUnits, unusableLots, unusableUnits,
                drive.getNeeds().size(), recentHandovers(LocalDate.now()).size());
    }

    public void validate(NextDrive n) {
        if (n.name() == null || n.name().isBlank()) throw new IllegalArgumentException("Give the new drive a name");
        if (n.name().trim().length() > 60) throw new IllegalArgumentException("The drive name is too long (60 characters at most)");
        if (n.durationDays() < 1 || n.durationDays() > MAX_DAYS) {
            throw new IllegalArgumentException("Duration must be between 1 and " + MAX_DAYS + " days");
        }
        if (n.startDate() == null) throw new IllegalArgumentException("Choose a start date");
        if (n.startDate().isAfter(LocalDate.now())) throw new IllegalArgumentException("The start date cannot be in the future");
    }

    /**
     * Replaces the current run with a new one. Donors and families stay. Usable stock and the targets
     * carry over if asked. Handovers from the last repeat-window days always stay, so the fair-share
     * rule still holds on day one. Call validate() first, and archive the old run before this.
     */
    public Handover startNext(NextDrive n) {
        validate(n);
        String previous = drive.getName();
        LocalDate today = LocalDate.now();

        List<DonationItem> carried = new ArrayList<>();
        int lotsLeft = 0, unitsLeft = 0;
        for (DonationItem i : drive.getInventory()) {
            if (i.getQuantity() == 0) continue;
            if (n.carryStock() && i.isUsable()) {
                carried.add(i.carriedOver());          // polymorphic copy: each subclass copies its own fields
            } else {
                lotsLeft++;
                unitsLeft += i.getQuantity();
            }
        }
        List<Need> targets = new ArrayList<>();
        if (n.keepTargets()) {
            for (Need x : drive.getNeeds()) targets.add(new Need(x.getCategory(), x.getItemName(), x.getTargetQuantity()));
        }
        List<Distribution> history = recentHandovers(today);

        String location = n.location() == null || n.location().isBlank() ? drive.getLocation() : n.location().trim();
        drive.beginNext(n.name().trim(), location, n.startDate(), n.durationDays());

        for (Donor d : drive.getDonors()) d.getDonatedItemIds().clear();
        int units = 0;
        for (DonationItem i : carried) {
            drive.getInventory().add(i);
            units += i.getQuantity();
            Donor d = drive.findDonor(i.getDonorId());
            if (d != null) d.addDonatedItemId(i.getItemId());
        }
        drive.getNeeds().addAll(targets);
        drive.getDistributions().addAll(history);

        drive.log(ActivityType.DRIVE, "Started " + drive.getName() + " in " + drive.getLocation() + " for "
                + n.durationDays() + (n.durationDays() == 1 ? " day" : " days") + " from " + n.startDate());
        if (!carried.isEmpty()) {
            drive.log(ActivityType.DRIVE, "Carried over " + carried.size() + (carried.size() == 1 ? " lot" : " lots")
                    + " (" + units + " units) from " + previous);
        }
        return new Handover(previous, carried.size(), units, lotsLeft, unitsLeft, targets.size(), history.size());
    }

    /** Handovers still inside the repeat window, same test as DistributionService. */
    private List<Distribution> recentHandovers(LocalDate today) {
        List<Distribution> out = new ArrayList<>();
        for (Distribution d : drive.getDistributions()) {
            if (d.isHandedOver() && d.getDate().plusDays(AppConstants.REPEAT_WINDOW_DAYS).isAfter(today)) out.add(d);
        }
        return out;
    }
}
