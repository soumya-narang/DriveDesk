package model.item;

import model.Category;
import model.Condition;
import util.IdGenerator;

import java.time.LocalDate;

public class BookItem extends DonationItem {
    private final String subject;
    private final String gradeLevel;
    private final Condition condition;

    public BookItem(String itemId, String name, int quantity, int initialQuantity, String donorId,
                    LocalDate receivedOn, String subject, String gradeLevel, Condition condition) {
        super(itemId, name, quantity, initialQuantity, donorId, receivedOn);
        this.subject = subject == null || subject.isBlank() ? "General" : subject.trim();
        this.gradeLevel = gradeLevel == null || gradeLevel.isBlank() ? "Any" : gradeLevel.trim();
        this.condition = condition == null ? Condition.GOOD : condition;
    }

    public BookItem(String name, int quantity, String donorId, String subject, String gradeLevel, Condition condition) {
        this(IdGenerator.nextItemId(), name, quantity, quantity, donorId, LocalDate.now(), subject, gradeLevel, condition);
    }

    public String getSubject() { return subject; }
    public String getGradeLevel() { return gradeLevel; }
    public Condition getCondition() { return condition; }

    @Override public Category getCategory() { return Category.BOOK; }
    @Override public boolean isUsable() { return condition != Condition.DAMAGED; }
    @Override public int getDistributionPriority() { return 100; }

    @Override
    protected String[] getExtras() { return new String[] { subject, gradeLevel, condition.name() }; }

    @Override
    public String describe() { return subject + " · grade " + gradeLevel + " · " + condition.name().toLowerCase(); }

    @Override
    public DonationItem carriedOver() {
        return new BookItem(getItemId(), getName(), getQuantity(), getQuantity(), getDonorId(), getReceivedOn(), subject, gradeLevel, condition);
    }
}
