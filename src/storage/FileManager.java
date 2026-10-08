package storage;

import contract.Persistable;
import model.ActivityEntry;
import model.ActivityType;
import model.Category;
import model.Condition;
import model.Distribution;
import model.Drive;
import model.Need;
import model.item.BookItem;
import model.item.ClothingItem;
import model.item.DonationItem;
import model.item.FoodItem;
import model.item.MedicineItem;
import model.person.Beneficiary;
import model.person.Donor;
import util.Csv;
import util.IdGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/** Reads and writes the CSV files in the data directory. */
public class FileManager {
    private final Path dir;

    public FileManager(String dataDir) {
        this.dir = Path.of(dataDir);
    }

    public boolean hasData() {
        return Files.exists(dir.resolve("drive.csv"));
    }

    public void saveAll(Drive drive) throws IOException {
        Files.createDirectories(dir);
        write("drive.csv", "name,location,startDate,durationDays",
                List.of(Csv.join(drive.getName(), drive.getLocation(), drive.getStartDate(), drive.getDurationDays())));
        write("donors.csv", "id,name,phone,organisation,donatedItemIds", lines(drive.getDonors()));
        write("beneficiaries.csv", "id,name,phone,familySize,address,priority", lines(drive.getBeneficiaries()));
        write("items.csv", "type,itemId,name,quantity,initialQty,donorId,receivedOn,extra1,extra2,extra3", lines(drive.getInventory()));
        write("needs.csv", "category,itemName,target", lines(drive.getNeeds()));
        write("distributions.csv", "id,beneficiaryId,itemId,category,itemName,quantity,date,status,note", lines(drive.getDistributions()));
        write("activity.csv", "time,type,text", lines(drive.getActivity()));
    }

    /** A closed drive kept under data/archive/, with the date it was closed. */
    public record ArchivedDrive(String folder, LocalDate closedOn, Drive drive) {}

    private static final String[] CSV_FILES = {
            "drive.csv", "donors.csv", "beneficiaries.csv", "items.csv", "needs.csv", "distributions.csv", "activity.csv" };
    private static final Pattern ARCHIVE_NAME = Pattern.compile("\\d{4}-\\d{2}-\\d{2}_[A-Za-z0-9-]+");

    /**
     * Saves, then copies the current CSVs and a text summary to data/archive/&lt;closed date&gt;_&lt;drive name&gt;/.
     * The copy is a complete drive, so it can be loaded again later.
     */
    public Path archive(Drive drive, String summaryText) throws IOException {
        saveAll(drive);
        Path root = dir.resolve("archive");
        Files.createDirectories(root);
        String base = LocalDate.now() + "_" + slug(drive.getName());
        Path target = root.resolve(base);
        for (int n = 2; Files.exists(target); n++) target = root.resolve(base + "-" + n);
        Files.createDirectories(target);
        for (String f : CSV_FILES) Files.copy(dir.resolve(f), target.resolve(f));
        Files.writeString(target.resolve("summary.txt"), summaryText, StandardCharsets.UTF_8);
        return target;
    }

    /** Closed drives, newest first. A folder that cannot be read is skipped. */
    public List<ArchivedDrive> listArchive() throws IOException {
        Path root = dir.resolve("archive");
        List<ArchivedDrive> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        List<Path> folders;
        try (Stream<Path> s = Files.list(root)) {
            folders = s.filter(Files::isDirectory).sorted(Comparator.reverseOrder()).toList();
        }
        for (Path p : folders) {
            String name = p.getFileName().toString();
            if (!ARCHIVE_NAME.matcher(name).matches()) continue;
            try {
                out.add(new ArchivedDrive(name, LocalDate.parse(name.substring(0, 10)), new FileManager(p.toString()).loadAll()));
            } catch (IOException | RuntimeException e) {
                System.err.println("Skipping unreadable archive " + name + ": " + e.getMessage());
            }
        }
        return out;
    }

    /** The saved summary of a closed drive, or null if there is no such archive. */
    public String readArchivedSummary(String folder) throws IOException {
        if (folder == null || !ARCHIVE_NAME.matcher(folder).matches()) return null;
        Path p = dir.resolve("archive").resolve(folder).resolve("summary.txt");
        return Files.exists(p) ? Files.readString(p, StandardCharsets.UTF_8) : null;
    }

    private static String slug(String name) {
        String s = name.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        if (s.length() > 40) s = s.substring(0, 40).replaceAll("-$", "");
        return s.isEmpty() ? "drive" : s;
    }

    public Drive loadAll() throws IOException {
        List<String> meta = read("drive.csv");
        if (meta.isEmpty()) throw new IOException("No saved drive found in " + dir);
        List<String> m = Csv.parse(meta.get(0));
        Drive drive = new Drive(m.get(0), m.get(1), LocalDate.parse(m.get(2)), Integer.parseInt(m.get(3)));

        for (String line : read("donors.csv")) {
            List<String> f = Csv.parse(line);
            Donor d = new Donor(f.get(0), f.get(1), f.get(2), Boolean.parseBoolean(f.get(3)));
            if (f.size() > 4 && !f.get(4).isBlank()) {
                for (String id : f.get(4).split(";")) d.addDonatedItemId(id);
            }
            drive.getDonors().add(d);
            IdGenerator.observe(d.getId());
        }
        for (String line : read("beneficiaries.csv")) {
            List<String> f = Csv.parse(line);
            Beneficiary b = new Beneficiary(f.get(0), f.get(1), f.get(2), Integer.parseInt(f.get(3)), f.get(4),
                    Integer.parseInt(f.get(5)));
            drive.getBeneficiaries().add(b);
            IdGenerator.observe(b.getId());
        }
        for (String line : read("items.csv")) {
            DonationItem item = parseItem(Csv.parse(line));
            drive.getInventory().add(item);
            IdGenerator.observe(item.getItemId());
        }
        for (String line : read("needs.csv")) {
            List<String> f = Csv.parse(line);
            drive.getNeeds().add(new Need(Category.parse(f.get(0)), f.get(1), Integer.parseInt(f.get(2))));
        }
        for (String line : read("distributions.csv")) {
            List<String> f = Csv.parse(line);
            Distribution d = new Distribution(f.get(0), f.get(1), f.get(2), Category.parse(f.get(3)), f.get(4),
                    Integer.parseInt(f.get(5)), LocalDate.parse(f.get(6)), Distribution.Status.valueOf(f.get(7)),
                    f.size() > 8 ? f.get(8) : "");
            drive.getDistributions().add(d);
            IdGenerator.observe(d.getId());
        }
        for (String line : read("activity.csv")) {
            List<String> f = Csv.parse(line);
            drive.getActivity().add(new ActivityEntry(LocalDateTime.parse(f.get(0)), ActivityType.valueOf(f.get(1)), f.get(2)));
        }
        return drive;
    }

    /** The "type" column decides which subclass to create. */
    private DonationItem parseItem(List<String> f) {
        Category type = Category.parse(f.get(0));
        String id = f.get(1), name = f.get(2), donor = f.get(5);
        int qty = Integer.parseInt(f.get(3)), initial = Integer.parseInt(f.get(4));
        LocalDate received = LocalDate.parse(f.get(6));
        String e1 = f.size() > 7 ? f.get(7) : "", e2 = f.size() > 8 ? f.get(8) : "", e3 = f.size() > 9 ? f.get(9) : "";
        return switch (type) {
            case FOOD -> new FoodItem(id, name, qty, initial, donor, received, LocalDate.parse(e1), Boolean.parseBoolean(e2));
            case MEDICINE -> new MedicineItem(id, name, qty, initial, donor, received, LocalDate.parse(e1), Boolean.parseBoolean(e2));
            case CLOTHING -> new ClothingItem(id, name, qty, initial, donor, received, e1, e2, Condition.parse(e3));
            case BOOK -> new BookItem(id, name, qty, initial, donor, received, e1, e2, Condition.parse(e3));
        };
    }

    private List<String> lines(List<? extends Persistable> items) {
        List<String> out = new ArrayList<>();
        for (Persistable p : items) out.add(p.toCsv());
        return out;
    }

    private void write(String file, String header, List<String> rows) throws IOException {
        List<String> all = new ArrayList<>();
        all.add(header);
        all.addAll(rows);
        Path target = dir.resolve(file);
        Path tmp = dir.resolve(file + ".tmp");
        Files.write(tmp, all, StandardCharsets.UTF_8);
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
    }

    /** Returns data rows (header skipped); a missing file is an empty list. */
    private List<String> read(String file) throws IOException {
        Path p = dir.resolve(file);
        if (!Files.exists(p)) return new ArrayList<>();
        List<String> all = Files.readAllLines(p, StandardCharsets.UTF_8);
        List<String> rows = new ArrayList<>();
        for (int i = 1; i < all.size(); i++) {
            if (!all.get(i).isBlank()) rows.add(all.get(i));
        }
        return rows;
    }
}
