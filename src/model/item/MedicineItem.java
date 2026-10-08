package model.item;

import contract.Expirable;
import model.Category;
import util.IdGenerator;

import java.time.LocalDate;

public class MedicineItem extends DonationItem implements Expirable {
    private final LocalDate expiryDate;
    private final boolean sealed;

    public MedicineItem(String itemId, String name, int quantity, int initialQuantity, String donorId,
                        LocalDate receivedOn, LocalDate expiryDate, boolean sealed) {
        super(itemId, name, quantity, initialQuantity, donorId, receivedOn);
        if (expiryDate == null) throw new IllegalArgumentException("Expiry date is required for medicine");
        this.expiryDate = expiryDate;
        this.sealed = sealed;
    }

    public MedicineItem(String name, int quantity, String donorId, LocalDate expiryDate, boolean sealed) {
        this(IdGenerator.nextItemId(), name, quantity, quantity, donorId, LocalDate.now(), expiryDate, sealed);
    }

    public boolean isSealed() { return sealed; }

    @Override public Category getCategory() { return Category.MEDICINE; }
    @Override public LocalDate getExpiryDate() { return expiryDate; }
    @Override public boolean isUsable() { return daysToExpiry() >= 0 && sealed; }
    @Override public int getDistributionPriority() { return (int) daysToExpiry(); }

    @Override
    protected String[] getExtras() { return new String[] { expiryDate.toString(), String.valueOf(sealed) }; }

    @Override
    public String describe() { return "Expires " + expiryDate + (sealed ? " · sealed" : " · NOT sealed"); }

    @Override
    public DonationItem carriedOver() {
        return new MedicineItem(getItemId(), getName(), getQuantity(), getQuantity(), getDonorId(), getReceivedOn(), expiryDate, sealed);
    }
}
