package model.person;

import util.Csv;

import java.util.ArrayList;

public class Donor extends Person {
    private final boolean organisation;
    private final ArrayList<String> donatedItemIds = new ArrayList<>();

    public Donor(String id, String name, String phone, boolean organisation) {
        super(id, name, phone);
        this.organisation = organisation;
    }

    public boolean isOrganisation() { return organisation; }
    public ArrayList<String> getDonatedItemIds() { return donatedItemIds; }

    public void addDonatedItemId(String itemId) {
        if (!donatedItemIds.contains(itemId)) donatedItemIds.add(itemId);
    }

    @Override
    public String getRole() { return "Donor"; }

    @Override
    public String toCsv() {
        return Csv.join(getId(), getName(), getPhone(), organisation, String.join(";", donatedItemIds));
    }
}
