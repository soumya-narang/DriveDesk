package model.item;

import model.Category;
import model.Condition;
import util.IdGenerator;

import java.time.LocalDate;

public class ClothingItem extends DonationItem {
    private final String size;
    private final String season;
    private final Condition condition;

    public ClothingItem(String itemId, String name, int quantity, int initialQuantity, String donorId,
                        LocalDate receivedOn, String size, String season, Condition condition) {
        super(itemId, name, quantity, initialQuantity, donorId, receivedOn);
        this.size = size == null || size.isBlank() ? "Free" : size.trim();
        this.season = season == null || season.isBlank() ? "All" : season.trim();
        this.condition = condition == null ? Condition.GOOD : condition;
    }

    public ClothingItem(String name, int quantity, String donorId, String size, String season, Condition condition) {
        this(IdGenerator.nextItemId(), name, quantity, quantity, donorId, LocalDate.now(), size, season, condition);
    }

    public String getSize() { return size; }
    public String getSeason() { return season; }
    public Condition getCondition() { return condition; }

    @Override public Category getCategory() { return Category.CLOTHING; }
    @Override public boolean isUsable() { return condition != Condition.DAMAGED; }
    @Override public int getDistributionPriority() { return 100; }

    @Override
    protected String[] getExtras() { return new String[] { size, season, condition.name() }; }

    @Override
    public String describe() { return "Size " + size + " · " + season + " · " + condition.name().toLowerCase(); }

    @Override
    public DonationItem carriedOver() {
        return new ClothingItem(getItemId(), getName(), getQuantity(), getQuantity(), getDonorId(), getReceivedOn(), size, season, condition);
    }
}
