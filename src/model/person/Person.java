package model.person;

import contract.Persistable;

public abstract class Person implements Persistable {
    private final String id;
    private String name;
    private String phone;

    protected Person(String id, String name, String phone) {
        this.id = id;
        setName(name);
        setPhone(phone);
    }

    public abstract String getRole();

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }

    public void setName(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Name is required");
        this.name = name.trim();
    }

    /** Phone numbers are stored without spaces, dashes or brackets so they compare reliably. */
    public void setPhone(String phone) {
        this.phone = phone == null ? "" : phone.replaceAll("[\\s\\-()]", "");
    }

    @Override
    public String toString() {
        return getRole() + " " + id + " " + name;
    }
}
