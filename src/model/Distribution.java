package model;

import contract.Persistable;
import util.Csv;

import java.time.LocalDate;

/** A record that a beneficiary received (or was blocked from receiving) items on a date. */
public class Distribution implements Persistable {
    public enum Status { HANDED_OVER, BLOCKED }

    private final String id;
    private final String beneficiaryId;
    private final String itemId;      // empty for a blocked attempt
    private final Category category;
    private final String itemName;
    private final int quantity;
    private final LocalDate date;
    private final Status status;
    private final String note;        // reason, for blocked attempts

    public Distribution(String id, String beneficiaryId, String itemId, Category category, String itemName,
                        int quantity, LocalDate date, Status status, String note) {
        this.id = id;
        this.beneficiaryId = beneficiaryId;
        this.itemId = itemId == null ? "" : itemId;
        this.category = category;
        this.itemName = itemName == null ? "" : itemName;
        this.quantity = quantity;
        this.date = date;
        this.status = status;
        this.note = note == null ? "" : note;
    }

    public String getId() { return id; }
    public String getBeneficiaryId() { return beneficiaryId; }
    public String getItemId() { return itemId; }
    public Category getCategory() { return category; }
    public String getItemName() { return itemName; }
    public int getQuantity() { return quantity; }
    public LocalDate getDate() { return date; }
    public Status getStatus() { return status; }
    public String getNote() { return note; }

    public boolean isHandedOver() { return status == Status.HANDED_OVER; }

    @Override
    public String toCsv() {
        return Csv.join(id, beneficiaryId, itemId, category, itemName, quantity, date, status, note);
    }

    @Override
    public String toString() {
        return id + " " + beneficiaryId + " " + category + " x" + quantity + " " + date + " " + status;
    }
}
