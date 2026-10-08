package model;

import model.item.DonationItem;
import model.person.Beneficiary;
import model.person.Donor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;

/** One donation drive. Composition: a drive HAS donors, beneficiaries, inventory, needs, distributions. */
public class Drive {
    private String name;
    private String location;
    private LocalDate startDate;
    private int durationDays;

    private final ArrayList<Donor> donors = new ArrayList<>();
    private final ArrayList<Beneficiary> beneficiaries = new ArrayList<>();
    private final ArrayList<DonationItem> inventory = new ArrayList<>();
    private final ArrayList<Need> needs = new ArrayList<>();
    private final ArrayList<Distribution> distributions = new ArrayList<>();
    private final ArrayList<ActivityEntry> activity = new ArrayList<>();

    public Drive(String name, String location, LocalDate startDate, int durationDays) {
        this.name = name;
        this.location = location;
        this.startDate = startDate;
        this.durationDays = durationDays;
    }

    public String getName() { return name; }
    public String getLocation() { return location; }
    public LocalDate getStartDate() { return startDate; }
    public int getDurationDays() { return durationDays; }

    public ArrayList<Donor> getDonors() { return donors; }
    public ArrayList<Beneficiary> getBeneficiaries() { return beneficiaries; }
    public ArrayList<DonationItem> getInventory() { return inventory; }
    public ArrayList<Need> getNeeds() { return needs; }
    public ArrayList<Distribution> getDistributions() { return distributions; }
    public ArrayList<ActivityEntry> getActivity() { return activity; }

    /** 1-based day of the drive, clamped to its length. */
    public int getCurrentDay() {
        long d = ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1;
        return (int) Math.max(1, Math.min(d, durationDays));
    }

    /** Last day of the drive (inclusive). */
    public LocalDate getEndDate() { return startDate.plusDays(durationDays - 1L); }

    /** Day number counted from the start, not clamped: it passes getDurationDays() once the drive is over. */
    public long getElapsedDay() { return ChronoUnit.DAYS.between(startDate, LocalDate.now()) + 1; }

    public boolean isOver() { return getElapsedDay() > durationDays; }

    public int getDaysLeft() { return (int) Math.max(0, durationDays - getElapsedDay()); }

    /** True for dates on or after this drive's start; earlier dates belong to a previous drive. */
    public boolean isInDrive(LocalDate date) { return !date.isBefore(startDate); }

    /**
     * Starts the next run of the drive: sets the new name and dates and empties everything that belongs to
     * one run (stock, targets, handovers, log). Donors and beneficiaries stay; they are people, not a run.
     */
    public void beginNext(String name, String location, LocalDate startDate, int durationDays) {
        this.name = name;
        this.location = location;
        this.startDate = startDate;
        this.durationDays = durationDays;
        inventory.clear();
        needs.clear();
        distributions.clear();
        activity.clear();
    }

    public Donor findDonor(String id) {
        for (Donor d : donors) if (d.getId().equals(id)) return d;
        return null;
    }

    public Beneficiary findBeneficiary(String id) {
        for (Beneficiary b : beneficiaries) if (b.getId().equals(id)) return b;
        return null;
    }

    public DonationItem findItem(String id) {
        for (DonationItem i : inventory) if (i.getItemId().equals(id)) return i;
        return null;
    }

    public void log(ActivityType type, String text) {
        log(type, text, LocalDateTime.now());
    }

    public void log(ActivityType type, String text, LocalDateTime time) {
        activity.add(new ActivityEntry(time, type, text));
    }
}
