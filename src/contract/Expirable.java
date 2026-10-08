package contract;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** A capability, not an "is-a": only some items expire. */
public interface Expirable {
    LocalDate getExpiryDate();

    default long daysToExpiry() {
        return ChronoUnit.DAYS.between(LocalDate.now(), getExpiryDate());
    }

    default boolean isNearExpiry(int withinDays) {
        return daysToExpiry() <= withinDays;
    }
}
