package model;

import contract.Persistable;
import util.Csv;

/** A target such as "Blanket x 200". */
public class Need implements Persistable {
    private final Category category;
    private final String itemName;
    private int targetQuantity;

    public Need(Category category, String itemName, int targetQuantity) {
        if (itemName == null || itemName.isBlank()) throw new IllegalArgumentException("Item name is required");
        if (targetQuantity < 1) throw new IllegalArgumentException("Target must be at least 1");
        this.category = category;
        this.itemName = itemName.trim();
        this.targetQuantity = targetQuantity;
    }

    public Category getCategory() { return category; }
    public String getItemName() { return itemName; }
    public int getTargetQuantity() { return targetQuantity; }

    public void setTargetQuantity(int targetQuantity) {
        if (targetQuantity < 1) throw new IllegalArgumentException("Target must be at least 1");
        this.targetQuantity = targetQuantity;
    }

    /** A donated item counts toward this need when it is in the same category and its name contains the need name. */
    public boolean matches(Category itemCategory, String name) {
        return category == itemCategory && name.toLowerCase().contains(itemName.toLowerCase());
    }

    @Override
    public String toCsv() { return Csv.join(category, itemName, targetQuantity); }

    @Override
    public String toString() { return itemName + " x" + targetQuantity; }
}
