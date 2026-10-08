package model;

public enum Category {
    FOOD("Food"),
    CLOTHING("Clothing"),
    MEDICINE("Medicine"),
    BOOK("Books");

    private final String label;

    Category(String label) { this.label = label; }

    public String getLabel() { return label; }

    public static Category parse(String s) {
        if (s == null) throw new IllegalArgumentException("Category is required");
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown category: " + s);
        }
    }
}
