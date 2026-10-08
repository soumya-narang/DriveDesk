package model;

public enum Condition {
    NEW, GOOD, WORN, DAMAGED;

    public static Condition parse(String s) {
        if (s == null || s.isBlank()) return GOOD;
        try {
            return valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown condition: " + s);
        }
    }
}
