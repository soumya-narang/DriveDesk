package contract;

/** Anything that can be written as one CSV line. */
public interface Persistable {
    String toCsv();
}
