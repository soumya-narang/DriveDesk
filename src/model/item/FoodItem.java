package model.item;

import contract.Expirable;
import model.Category;
import util.IdGenerator;

import java.time.LocalDate;

public class FoodItem extends DonationItem implements Expirable {
    private final LocalDate expiryDate;
    private final boolean perishable;

    /** Full constructor (used when loading from disk). */
    public FoodItem(String itemId, String name, int quantity, int initialQuantity, String donorId,
                    LocalDate receivedOn, LocalDate expiryDate, boolean perishable) {
        super(itemId, name, quantity, initialQuantity, donorId, receivedOn);
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is required for food");
        this.expiryDate = expiryDate;
        this.perishable = perishable;
    }

    public FoodItem(String name, int quantity, String donorId, LocalDate expiryDate) {
        this(name, quantity, donorId, expiryDate, false);
    }

    public FoodItem(String name, int quantity, String donorId, LocalDate expiryDate, boolean perishable) {
        this(IdGenerator.nextItemId(), name, quantity, quantity, donorId, LocalDate.now(), expiryDate, perishable);
    }

    public boolean isPerishable() { return perishable; }

    @Override public Category getCategory() { return Category.FOOD; }
    @Override public LocalDate getExpiryDate() { return expiryDate; }
    @Override public boolean isUsable() { return daysToExpiry() >= 0; }
    @Override public int getDistributionPriority() { return (int) daysToExpiry(); }

    @Override
    protected String[] getExtras() { return new String[] { expiryDate.toString(), String.valueOf(perishable) }; }

    @Override
    public String describe() { return "Expires " + expiryDate + (perishable ? " · perishable" : ""); }

    @Override
    public DonationItem carriedOver() {
        return new FoodItem(getItemId(), getName(), getQuantity(), getQuantity(), getDonorId(), getReceivedOn(), expiryDate, perishable);
    }
}
