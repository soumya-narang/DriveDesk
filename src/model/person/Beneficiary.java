package model.person;

import util.Csv;

public class Beneficiary extends Person {
    private int familySize;
    private String address;
    private int priorityLevel; // 1 = most urgent, 3 = least

    public Beneficiary(String id, String name, String phone, int familySize, String address, int priorityLevel) {
        super(id, name, phone);
        if (getPhone().replaceAll("\\D", "").length() < 7) {
            throw new IllegalArgumentException("A phone number (at least 7 digits) is required to register a family");
        }
        if (familySize < 1) throw new IllegalArgumentException("Family size must be at least 1");
        if (priorityLevel < 1 || priorityLevel > 3) throw new IllegalArgumentException("Priority must be 1, 2 or 3");
        this.familySize = familySize;
        this.address = address == null ? "" : address.trim();
        this.priorityLevel = priorityLevel;
    }

    public int getFamilySize() { return familySize; }
    public String getAddress() { return address; }
    public int getPriorityLevel() { return priorityLevel; }

    @Override
    public String getRole() { return "Beneficiary"; }

    /** Two registrations with the same phone are the same family. */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Beneficiary)) return false;
        Beneficiary other = (Beneficiary) o;
        return this.getPhone().equals(other.getPhone());
    }

    @Override
    public int hashCode() { return getPhone().hashCode(); }

    @Override
    public String toCsv() {
        return Csv.join(getId(), getName(), getPhone(), familySize, address, priorityLevel);
    }
}
