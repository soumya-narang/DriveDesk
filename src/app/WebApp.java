package app;

import model.Drive;
import storage.FileManager;
import util.AppConstants;
import web.WebServer;

/**
 * Starts the web UI. Usage: java -cp out app.WebApp [port] [--empty]
 * On a first run it seeds demo data (unless --empty is given) and saves it to data/.
 * When a host such as Render sets the PORT environment variable, that port is used and the server listens on all interfaces.
 */
public class WebApp {
    public static void main(String[] args) throws Exception {
        int port = AppConstants.DEFAULT_PORT;
        String host = "127.0.0.1";
        String hosted = System.getenv("PORT");
        if (hosted != null && !hosted.isBlank()) {
            port = Integer.parseInt(hosted.trim());
            host = "0.0.0.0";
        }
        boolean empty = false;
        for (String a : args) {
            if (a.equals("--empty")) empty = true;
            else port = Integer.parseInt(a);
        }

        FileManager files = new FileManager(AppConstants.DATA_DIR);
        Drive drive;
        if (files.hasData()) {
            drive = files.loadAll();
            System.out.println("Loaded saved drive from " + AppConstants.DATA_DIR);
        } else {
            drive = empty ? new Drive("My Donation Drive", "Your city", java.time.LocalDate.now(), 30) : DemoData.create();
            files.saveAll(drive);
            System.out.println(empty ? "Started an empty drive." : "First run: seeded demo data.");
        }

        WebServer server = new WebServer(host, port, AppConstants.WEB_DIR, drive, files);
        server.start();
        System.out.println("Donation Drive Manager running at http://localhost:" + port + "/  (Ctrl+C to stop)");
    }
}
