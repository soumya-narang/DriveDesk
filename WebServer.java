package web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import model.Drive;
import storage.FileManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;

/** Serves the JSON API under /api and the static UI from the web directory. Localhost only unless a host is given. */
public class WebServer {
    private static final Map<String, String> TYPES = Map.of(
            "html", "text/html; charset=utf-8",
            "css", "text/css; charset=utf-8",
            "js", "text/javascript; charset=utf-8",
            "svg", "image/svg+xml",
            "json", "application/json; charset=utf-8",
            "ico", "image/x-icon");

    private final HttpServer server;
    private final Path root;

    public WebServer(int port, String webDir, Drive drive, FileManager files) throws IOException {
        this("127.0.0.1", port, webDir, drive, files);
    }

    public WebServer(String host, int port, String webDir, Drive drive, FileManager files) throws IOException {
        this.root = Path.of(webDir).toAbsolutePath().normalize();
        this.server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.createContext("/api/", new ApiHandler(drive, files));
        server.createContext("/", this::serveStatic);
        server.setExecutor(Executors.newFixedThreadPool(4));
    }

    public void start() { server.start(); }

    private void serveStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        Path file = root.resolve(path.substring(1)).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) {
            byte[] msg = "Not found".getBytes();
            ex.sendResponseHeaders(404, msg.length);
            ex.getResponseBody().write(msg);
            ex.close();
            return;
        }
        String name = file.getFileName().toString();
        String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
        byte[] bytes = Files.readAllBytes(file);
        ex.getResponseHeaders().set("Content-Type", TYPES.getOrDefault(ext, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }
}
