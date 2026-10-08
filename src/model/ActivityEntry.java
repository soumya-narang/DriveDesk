package model;

import contract.Persistable;
import util.Csv;

import java.time.LocalDateTime;

public class ActivityEntry implements Persistable {
    private final LocalDateTime time;
    private final ActivityType type;
    private final String text;

    public ActivityEntry(LocalDateTime time, ActivityType type, String text) {
        this.time = time;
        this.type = type;
        this.text = text;
    }

    public LocalDateTime getTime() { return time; }
    public ActivityType getType() { return type; }
    public String getText() { return text; }

    @Override
    public String toCsv() { return Csv.join(time, type, text); }

    @Override
    public String toString() { return time + " " + type + " " + text; }
}
