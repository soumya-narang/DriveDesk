package exception;

public class InsufficientStockException extends Exception {
    private final int available;
    private final int requested;

    public InsufficientStockException(String itemName, int available, int requested) {
        super("Only " + available + " of " + itemName + " in stock, " + requested + " requested");
        this.available = available;
        this.requested = requested;
    }

    public int getAvailable() { return available; }
    public int getRequested() { return requested; }
}
