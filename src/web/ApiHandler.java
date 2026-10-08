package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import contract.Expirable;
import exception.DistributionNotAllowedException;
import exception.DuplicateBeneficiaryException;
import exception.InsufficientStockException;
import model.ActivityEntry;
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
import service.DistributionService;
import service.DriveService;
import service.InventoryService;
import service.NeedsService;
import service.ReportService;
import storage.FileManager;
import util.AppConstants;
import util.IdGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * JSON API. The UI only calls this; every rule lives in the service layer.
 * A single lock serialises requests, and every write request is saved to disk.
 */
public class ApiHandler implements HttpHandler {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd MMM HH:mm", Locale.ENGLISH);
    private static final int TREND_DAYS = 14;

    private final Drive drive;
    private final FileManager files;
    private final InventoryService inventory;
    private final NeedsService needs;
    private final DistributionService distribution;
    private final ReportService reports;
    private final DriveService driveService;
    private final Object lock = new Object();

    public ApiHandler(Drive drive, FileManager files) {
        this.drive = drive;
        this.files = files;
        this.inventory = new InventoryService(drive);
        this.needs = new NeedsService(drive);
        this.distribution = new DistributionService(drive, inventory);
        this.reports = new ReportService(drive, inventory, needs);
        this.driveService = new DriveService(drive);
    }

    // ------------------------------------------------------------------ plumbing

    @Override
    public void handle(HttpExchange ex) throws IOException {
        int status = 200;
        Object payload;
        String method = ex.getRequestMethod();
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> query = parseQuery(ex.getRequestURI().getRawQuery());
            Map<String, Object> body = new LinkedHashMap<>();
            if (!method.equals("GET")) {
                body = Json.parseObject(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            }
            synchronized (lock) {
                try {
                    payload = route(method, path, query, new Body(body));
                } finally {
                    if (!method.equals("GET")) save();
                }
            }
        } catch (NotFound e) {
            status = 404;
            payload = error(e.getMessage(), "NOT_FOUND");
        } catch (DuplicateBeneficiaryException e) {
            status = 409;
            Map<String, Object> dup = error(e.getMessage(), "DUP");
            dup.put("matchId", e.getExisting().getId());
            dup.put("matchName", e.getExisting().getName());
            dup.put("candidateId", e.getCandidateId());
            payload = dup;
        } catch (DistributionNotAllowedException e) {
            status = 409;
            payload = error(e.getMessage(), "BLOCK");
        } catch (InsufficientStockException e) {
            status = 409;
            payload = error(e.getMessage(), "STOCK");
        } catch (IllegalArgumentException | DateTimeParseException e) {
            status = 400;
            payload = error(e.getMessage(), "INVALID");
        } catch (Exception e) {
            e.printStackTrace();
            status = 500;
            payload = error("Server error: " + e.getMessage(), "SERVER");
        }
        byte[] bytes = Json.stringify(payload).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void save() {
        try {
            files.saveAll(drive);
        } catch (IOException e) {
            System.err.println("Could not save data: " + e.getMessage());
        }
    }

    private static final class NotFound extends RuntimeException {
        NotFound(String m) { super(m); }
    }

    /** Reports are built with %n, which is CRLF on Windows; the browser wants plain LF. */
    private static String unixLines(String text) { return text.replace("\r\n", "\n"); }

    private static Map<String, Object> error(String message, String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("error", message);
        m.put("code", code);
        return m;
    }

    private static Map<String, String> parseQuery(String raw) {
        Map<String, String> q = new LinkedHashMap<>();
        if (raw == null || raw.isEmpty()) return q;
        for (String pair : raw.split("&")) {
            int eq = pair.indexOf('=');
            String k = eq < 0 ? pair : pair.substring(0, eq);
            String v = eq < 0 ? "" : pair.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }

    /** Typed access to a request body. */
    private static final class Body {
        private final Map<String, Object> m;
        Body(Map<String, Object> m) { this.m = m; }

        String str(String k) {
            Object v = m.get(k);
            return v == null ? "" : (v instanceof Double d && d == Math.floor(d) ? String.valueOf(d.longValue()) : v.toString()).trim();
        }

        int integer(String k, int def) {
            Object v = m.get(k);
            if (v == null || v.toString().isBlank()) return def;
            if (v instanceof Number n) return n.intValue();
            try {
                return Integer.parseInt(v.toString().trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("'" + k + "' must be a whole number");
            }
        }

        boolean bool(String k) {
            Object v = m.get(k);
            return v instanceof Boolean b ? b : v != null && Boolean.parseBoolean(v.toString());
        }
    }

    // ------------------------------------------------------------------ routing

    private Object route(String method, String path, Map<String, String> q, Body b) throws Exception {
        boolean get = method.equals("GET"), post = method.equals("POST");
        if (get && path.equals("/api/meta")) return meta();
        if (get && path.equals("/api/overview")) return overview();
        if (get && path.equals("/api/donors")) return donors();
        if (post && path.equals("/api/donors")) return createDonor(b);
        if (get && path.equals("/api/inventory")) return inventoryList();
        if (post && path.equals("/api/donations")) return createDonation(b);
        if (get && path.equals("/api/needs")) return needsList();
        if (post && path.equals("/api/needs")) return setNeed(b);
        if (get && path.equals("/api/beneficiaries")) return beneficiaries();
        if (post && path.equals("/api/beneficiaries")) return createBeneficiary(b);
        if (get && path.equals("/api/distribution/suggest")) return suggest(Category.parse(q.get("category")));
        if (get && path.equals("/api/distributions")) return distributionList();
        if (post && path.equals("/api/distributions")) return createDistribution(b);
        if (get && path.equals("/api/activity")) return activityList(60);
        if (get && path.equals("/api/drive")) return currentDrive();
        if (post && path.equals("/api/drive/next")) return startNextDrive(b);
        if (get && path.equals("/api/drives/past")) return pastDrives();
        if (get && path.equals("/api/drives/past/summary")) {
            String text = files.readArchivedSummary(q.getOrDefault("folder", ""));
            if (text == null) throw new NotFound("No such past drive");
            return Map.of("text", unixLines(text));
        }
        if (get && path.equals("/api/reports/summary")) return Map.of("text", unixLines(reports.driveSummary()));
        if (get && path.equals("/api/reports/acknowledgement")) {
            return Map.of("text", unixLines(reports.donorAcknowledgement(q.getOrDefault("donorId", ""))));
        }
        throw new NotFound("No such endpoint: " + method + " " + path);
    }

    // ------------------------------------------------------------------ handlers

    private Object meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("drive", driveView());
        List<DonationItem> soon = inventory.getExpiringSoon(AppConstants.NEAR_EXPIRY_DAYS);
        m.put("expiringCount", soon.size());
        m.put("categories", categoryList());
        return m;
    }

    private Map<String, Object> driveView() {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("name", drive.getName());
        d.put("location", drive.getLocation());
        d.put("day", drive.getCurrentDay());
        d.put("totalDays", drive.getDurationDays());
        d.put("startDate", drive.getStartDate());
        d.put("endDate", drive.getEndDate());
        d.put("daysLeft", drive.getDaysLeft());
        d.put("ended", drive.isOver());
        return d;
    }

    private List<Map<String, Object>> categoryList() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Category c : Category.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c);
            m.put("label", c.getLabel());
            out.add(m);
        }
        return out;
    }

    private static Map<String, Object> statsView(DriveService.Stats st) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lots", st.lots());
        m.put("unitsReceived", st.unitsReceived());
        m.put("unitsHandedOut", st.unitsHandedOut());
        m.put("familiesServed", st.familiesServed());
        return m;
    }

    /** The Drive page: this drive's numbers, plus what starting the next drive would carry over. */
    private Object currentDrive() {
        Map<String, Object> m = driveView();
        m.put("stats", statsView(DriveService.statsOf(drive)));
        m.put("unitsInStock", inventory.totalUnitsInStock());
        m.put("donors", drive.getDonors().size());
        m.put("families", drive.getBeneficiaries().size());
        DriveService.Preview p = driveService.preview();
        Map<String, Object> pv = new LinkedHashMap<>();
        pv.put("usableLots", p.usableLots());
        pv.put("usableUnits", p.usableUnits());
        pv.put("unusableLots", p.unusableLots());
        pv.put("unusableUnits", p.unusableUnits());
        pv.put("targets", p.targets());
        pv.put("recentHandovers", p.recentHandovers());
        pv.put("windowDays", AppConstants.REPEAT_WINDOW_DAYS);
        m.put("preview", pv);
        return m;
    }

    private Object pastDrives() throws IOException {
        List<Object> out = new ArrayList<>();
        for (FileManager.ArchivedDrive a : files.listArchive()) {
            Drive d = a.drive();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("folder", a.folder());
            m.put("name", d.getName());
            m.put("location", d.getLocation());
            m.put("startDate", d.getStartDate());
            m.put("durationDays", d.getDurationDays());
            m.put("closedOn", a.closedOn());
            m.putAll(statsView(DriveService.statsOf(d)));
            out.add(m);
        }
        return out;
    }

    /** Archives the current drive first (so nothing is lost if that fails), then starts the next one. */
    private Object startNextDrive(Body b) throws IOException {
        String start = b.str("startDate");
        DriveService.NextDrive next = new DriveService.NextDrive(b.str("name"), b.str("location"),
                start.isEmpty() ? LocalDate.now() : LocalDate.parse(start), b.integer("durationDays", 30),
                b.bool("carryStock"), b.bool("keepTargets"));
        driveService.validate(next);
        Path archived = files.archive(drive, reports.driveSummary());
        DriveService.Handover r = driveService.startNext(next);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previousName", r.previousName());
        m.put("archivedAs", archived.getFileName().toString());
        m.put("lotsCarried", r.lotsCarried());
        m.put("unitsCarried", r.unitsCarried());
        m.put("lotsLeftBehind", r.lotsLeftBehind());
        m.put("unitsLeftBehind", r.unitsLeftBehind());
        m.put("targetsKept", r.targetsKept());
        m.put("handoversKept", r.handoversKept());
        m.put("drive", driveView());
        return m;
    }

    private Object overview() {
        LocalDate today = LocalDate.now();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("drive", driveView());

        // heatmap + trends over the last TREND_DAYS days (oldest first)
        List<LocalDate> days = new ArrayList<>();
        for (int i = TREND_DAYS - 1; i >= 0; i--) days.add(today.minusDays(i));
        int[] stockIn = new int[TREND_DAYS];
        int[] handedOut = new int[TREND_DAYS];
        Map<Category, int[]> perCategory = new LinkedHashMap<>();
        for (Category c : Category.values()) perCategory.put(c, new int[TREND_DAYS]);
        for (DonationItem i : drive.getInventory()) {
            int idx = days.indexOf(i.getReceivedOn());
            if (idx >= 0) stockIn[idx] += i.getInitialQuantity();
        }
        Set<String> served = new HashSet<>();
        for (Distribution d : drive.getDistributions()) {
            if (!d.isHandedOver()) continue;
            if (drive.isInDrive(d.getDate())) served.add(d.getBeneficiaryId());
            int idx = days.indexOf(d.getDate());
            if (idx >= 0) {
                handedOut[idx] += d.getQuantity();
                perCategory.get(d.getCategory())[idx] += d.getQuantity();
            }
        }
        int max = 0;
        for (int[] row : perCategory.values()) for (int v : row) max = Math.max(max, v);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Category, int[]> e : perCategory.entrySet()) {
            int[] levels = new int[TREND_DAYS];
            for (int i = 0; i < TREND_DAYS; i++) {
                int v = e.getValue()[i];
                levels[i] = v == 0 ? 0 : Math.max(1, Math.min(4, (int) Math.ceil(v * 4.0 / max)));
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("category", e.getKey());
            r.put("label", e.getKey().getLabel());
            r.put("values", e.getValue());
            r.put("levels", levels);
            rows.add(r);
        }
        Map<String, Object> heat = new LinkedHashMap<>();
        heat.put("days", days);
        heat.put("rows", rows);
        out.put("heatmap", heat);

        // needs
        List<NeedsService.NeedStatus> statuses = needs.getStillNeeded();
        List<Object> needViews = new ArrayList<>();
        int met = 0;
        for (NeedsService.NeedStatus s : statuses) {
            needViews.add(needView(s));
            if (s.isMet()) met++;
        }
        out.put("needs", needViews);

        // expiry runway
        List<DonationItem> soon = inventory.getExpiringSoon(AppConstants.NEAR_EXPIRY_DAYS);
        List<Object> expiry = new ArrayList<>();
        int urgent = 0;
        for (DonationItem i : soon) {
            long d = ((Expirable) i).daysToExpiry();
            if (d <= AppConstants.URGENT_EXPIRY_DAYS) urgent++;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("itemId", i.getItemId());
            m.put("name", i.getName());
            m.put("category", i.getCategory());
            m.put("quantity", i.getQuantity());
            m.put("days", d);
            expiry.add(m);
        }
        out.put("expiry", expiry);

        // stats
        int lots = inventory.getUsableStock().size();
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("unitsInStock", inventory.totalUnitsInStock());
        stats.put("lots", lots);
        stats.put("familiesServed", served.size());
        stats.put("familiesTotal", drive.getBeneficiaries().size());
        stats.put("targetsMet", met);
        stats.put("targetsTotal", statuses.size());
        stats.put("expiringCount", soon.size());
        stats.put("urgentCount", urgent);
        stats.put("soonestDays", soon.isEmpty() ? null : ((Expirable) soon.get(0)).daysToExpiry());
        stats.put("expiredCount", inventory.getExpiredStock().size());
        stats.put("stockInTrend", stockIn);
        stats.put("handedOutTrend", handedOut);
        stats.put("nearExpiryDays", AppConstants.NEAR_EXPIRY_DAYS);
        out.put("stats", stats);

        out.put("activity", activityList(12));
        List<Object> recent = distributionViews();
        out.put("recent", recent.subList(0, Math.min(8, recent.size())));
        return out;
    }

    private Map<String, Object> needView(NeedsService.NeedStatus s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", s.need().getCategory());
        m.put("itemName", s.need().getItemName());
        m.put("target", s.need().getTargetQuantity());
        m.put("collected", s.collected());
        m.put("remaining", s.remaining());
        m.put("met", s.isMet());
        return m;
    }

    private Map<String, Object> itemView(DonationItem i) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getItemId());
        m.put("category", i.getCategory());
        m.put("name", i.getName());
        m.put("quantity", i.getQuantity());
        m.put("initialQuantity", i.getInitialQuantity());
        m.put("donorId", i.getDonorId());
        Donor donor = drive.findDonor(i.getDonorId());
        m.put("donorName", donor == null ? "" : donor.getName());
        m.put("receivedOn", i.getReceivedOn());
        m.put("details", i.describe());
        m.put("usable", i.isUsable());
        Long days = null;
        String expiryStatus = "none";
        if (i instanceof Expirable e) {
            days = e.daysToExpiry();
            m.put("expiryDate", e.getExpiryDate());
            expiryStatus = days < 0 ? "expired"
                    : days <= AppConstants.URGENT_EXPIRY_DAYS ? "urgent"
                    : days <= AppConstants.NEAR_EXPIRY_DAYS ? "near" : "ok";
        }
        m.put("daysToExpiry", days);
        m.put("expiryStatus", expiryStatus);
        return m;
    }

    private Object inventoryList() {
        List<DonationItem> sorted = new ArrayList<>(drive.getInventory());
        sorted.sort(Comparator.comparing(DonationItem::getItemId).reversed());
        List<Object> out = new ArrayList<>();
        for (DonationItem i : sorted) out.add(itemView(i));
        return out;
    }

    private Object donors() {
        List<Object> out = new ArrayList<>();
        for (Donor d : drive.getDonors()) out.add(donorView(d));
        return out;
    }

    private Map<String, Object> donorView(Donor d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("name", d.getName());
        m.put("phone", d.getPhone());
        m.put("organisation", d.isOrganisation());
        m.put("donations", d.getDonatedItemIds().size());
        return m;
    }

    private Object createDonor(Body b) {
        Donor d = new Donor(IdGenerator.nextDonorId(), b.str("name"), b.str("phone"), b.bool("organisation"));
        drive.getDonors().add(d);
        return donorView(d);
    }

    private Object createDonation(Body b) {
        Category cat = Category.parse(b.str("category"));
        Donor donor = drive.findDonor(b.str("donorId"));
        if (donor == null) throw new IllegalArgumentException("Choose a donor first");
        String name = b.str("name");
        int qty = b.integer("quantity", 0);
        if (qty < 1) throw new IllegalArgumentException("Quantity must be at least 1");
        int idMark = IdGenerator.mark("I");
        try {
            DonationItem item = switch (cat) {
                case FOOD -> new FoodItem(name, qty, donor.getId(), date(b, "expiryDate"), b.bool("perishable"));
                case MEDICINE -> new MedicineItem(name, qty, donor.getId(), date(b, "expiryDate"), b.bool("sealed"));
                case CLOTHING -> new ClothingItem(name, qty, donor.getId(), b.str("size"), b.str("season"), Condition.parse(b.str("condition")));
                case BOOK -> new BookItem(name, qty, donor.getId(), b.str("subject"), b.str("gradeLevel"), Condition.parse(b.str("condition")));
            };
            inventory.addDonation(item, donor);
            return itemView(item);
        } catch (RuntimeException e) {
            IdGenerator.reset("I", idMark); // a rejected donation must not use up an item id
            throw e;
        }
    }

    private LocalDate date(Body b, String key) {
        String s = b.str(key);
        if (s.isEmpty()) throw new IllegalArgumentException("Expiry date is required");
        return LocalDate.parse(s);
    }

    private Object needsList() {
        List<Object> out = new ArrayList<>();
        for (NeedsService.NeedStatus s : needs.getStillNeeded()) out.add(needView(s));
        return out;
    }

    private Object setNeed(Body b) {
        Need n = needs.addNeed(Category.parse(b.str("category")), b.str("itemName"), b.integer("target", 0));
        return needView(new NeedsService.NeedStatus(n, needs.collected(n)));
    }

    private Object beneficiaries() {
        List<Object> out = new ArrayList<>();
        for (Beneficiary x : drive.getBeneficiaries()) out.add(beneficiaryView(x));
        return out;
    }

    private Map<String, Object> beneficiaryView(Beneficiary x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.getId());
        m.put("name", x.getName());
        m.put("phone", x.getPhone());
        m.put("familySize", x.getFamilySize());
        m.put("address", x.getAddress());
        m.put("priority", x.getPriorityLevel());
        int units = 0;
        LocalDate last = null;
        for (Distribution d : drive.getDistributions()) {
            if (d.isHandedOver() && d.getBeneficiaryId().equals(x.getId())) {
                units += d.getQuantity();
                if (last == null || d.getDate().isAfter(last)) last = d.getDate();
            }
        }
        m.put("unitsReceived", units);
        m.put("lastReceived", last);
        return m;
    }

    private Object createBeneficiary(Body b) throws DuplicateBeneficiaryException {
        int idMark = IdGenerator.mark("B");
        try {
            Beneficiary created = distribution.registerBeneficiary(b.str("name"), b.str("phone"),
                    b.integer("familySize", 1), b.str("address"), b.integer("priority", 2));
            return beneficiaryView(created);
        } catch (RuntimeException e) {
            IdGenerator.reset("B", idMark); // invalid input does not use up a family id (a duplicate does, on purpose: it is logged)
            throw e;
        }
    }

    private Object suggest(Category category) {
        DistributionService.Suggestion s = distribution.suggest(category);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("category", category);
        m.put("windowDays", AppConstants.REPEAT_WINDOW_DAYS);
        m.put("availableUnits", s.availableUnits());
        m.put("nextItem", s.nextItem() == null ? null : itemView(s.nextItem()));
        List<Object> eligible = new ArrayList<>();
        for (Beneficiary x : s.eligible()) eligible.add(beneficiaryView(x));
        List<Object> blocked = new ArrayList<>();
        for (DistributionService.BlockedFamily f : s.blocked()) {
            Map<String, Object> v = beneficiaryView(f.beneficiary());
            v.put("blockedSince", f.lastReceived());
            v.put("blockedUntil", f.lastReceived().plusDays(AppConstants.REPEAT_WINDOW_DAYS));
            blocked.add(v);
        }
        m.put("eligible", eligible);
        m.put("blocked", blocked);
        return m;
    }

    private List<Object> distributionViews() {
        List<Distribution> sorted = new ArrayList<>(drive.getDistributions());
        sorted.sort(Comparator.comparing(Distribution::getDate).thenComparing(Distribution::getId).reversed());
        List<Object> out = new ArrayList<>();
        for (Distribution d : sorted) {
            Beneficiary x = drive.findBeneficiary(d.getBeneficiaryId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", d.getId());
            m.put("date", d.getDate());
            m.put("beneficiaryId", d.getBeneficiaryId());
            m.put("beneficiaryName", x == null ? "" : x.getName());
            m.put("category", d.getCategory());
            m.put("itemId", d.getItemId());
            m.put("itemName", d.getItemName());
            m.put("quantity", d.getQuantity());
            m.put("status", d.getStatus());
            m.put("note", d.getNote());
            out.add(m);
        }
        return out;
    }

    private Object distributionList() { return distributionViews(); }

    private Object createDistribution(Body b) throws DistributionNotAllowedException, InsufficientStockException {
        List<Distribution> made = distribution.distribute(b.str("beneficiaryId"), Category.parse(b.str("category")),
                b.integer("quantity", 0), b.str("itemId"));
        int total = 0;
        List<Object> lots = new ArrayList<>();
        for (Distribution d : made) {
            total += d.getQuantity();
            lots.add(d.getQuantity() + " x " + d.getItemName() + " (" + d.getItemId() + ")");
        }
        Beneficiary x = drive.findBeneficiary(b.str("beneficiaryId"));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("lots", lots);
        m.put("beneficiaryId", x.getId());
        m.put("beneficiaryName", x.getName());
        return m;
    }

    private List<Object> activityList(int limit) {
        LocalDate today = LocalDate.now();
        List<ActivityEntry> all = new ArrayList<>(drive.getActivity());
        all.sort(Comparator.comparing(ActivityEntry::getTime).reversed());
        List<Object> out = new ArrayList<>();
        for (ActivityEntry a : all) {
            if (out.size() >= limit) break;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("when", a.getTime().toLocalDate().equals(today) ? a.getTime().format(TIME) : a.getTime().format(DAY_TIME));
            m.put("type", a.getType());
            m.put("text", a.getText());
            out.add(m);
        }
        return out;
    }
}
