package model.item;

import contract.Persistable;
import exception.InsufficientStockException;
import model.Category;
import util.Csv;

import java.time.LocalDate;

public abstract class DonationItem implements Persistable {
    private final String itemId;
    private String name;
    private int quantity;
    private final int initialQuantity;
    private String donorId;
    private final LocalDate receivedOn;

    protected DonationItem(String itemId, String name, int quantity, int initialQuantity,
                           String donorId, LocalDate receivedOn) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Item name is required");
        if (quantity < 0 || initialQuantity < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        this.itemId = itemId;
        this.name = name.trim();
        this.quantity = quantity;
        this.initialQuantity = initialQuantity;
        this.donorId = donorId == null ? "" : donorId;
        this.receivedOn = receivedOn;
    }

    public abstract Category getCategory();
    public abstract boolean isUsable();
    /** Lower = hand out first. */
    public abstract int getDistributionPriority();
    /** Subclass-specific fields written after the common ones in items.csv. */
    protected abstract String[] getExtras();
    /** Short human-readable detail line, e.g. "Expires 2026-12-31". */
    public abstract String describe();
    /** A copy of this lot for the next drive: same id, donor, dates and details; what is left now becomes the "received" amount. */
    public abstract DonationItem carriedOver();

    public String getItemId() { return itemId; }
    public String getName() { return name; }
    public int getQuantity() { return quantity; }
    public int getInitialQuantity() { return initialQuantity; }
    public String getDonorId() { return donorId; }
    public LocalDate getReceivedOn() { return receivedOn; }

    public void setDonorId(String donorId) { this.donorId = donorId == null ? "" : donorId; }

    public void reduceQuantity(int amount) throws InsufficientStockException {
        if (amount > quantity) {
            throw new InsufficientStockException(name, quantity, amount);
        }
        quantity -= amount;
    }

    @Override
    public String toCsv() {
        String[] extras = getExtras();
        Object[] fields = new Object[7 + extras.length];
        fields[0] = getCategory();
        fields[1] = itemId;
        fields[2] = name;
        fields[3] = quantity;
        fields[4] = initialQuantity;
        fields[5] = donorId;
        fields[6] = receivedOn;
        System.arraycopy(extras, 0, fields, 7, extras.length);
        return Csv.join(fields);
    }

    @Override
    public String toString() {
        return "[" + getCategory() + "] " + itemId + " " + name + " x" + quantity;
    }
}
