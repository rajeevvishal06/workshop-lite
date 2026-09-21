package demo;

import com.sun.net.httpserver.HttpServer;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * A tiny web app whose datastore IS the hero library.
 *
 * H2 1.4.200 carries the H2 Console RCE CRITICALs (CVE-2021-42392, CVE-2022-23221).
 * Here H2 is a real in-memory database queried on every request. The HTTP server is
 * the JDK's built-in {@code com.sun.net.httpserver} — no web framework, so H2 is the
 * only third-party library (and the only meaningful source of library CVEs).
 *
 * Nothing in this file changes across the three acts — only where the H2 jar is
 * resolved from changes (Maven Central -> Chainguard remediated).
 */
public class Demo {
    /**
     * The remediated build is a distinct Maven coordinate (1.4.200-0.cgr.3), but H2's
     * runtime driver version only reports the code line (1.4.200). The coordinate lives
     * in the jar FILENAME on the classpath, so we read it from the H2 Driver's code
     * source — that's what changes between acts and is what we show on screen.
     */
    static String h2Coordinate() {
        try {
            Class<?> h2 = Class.forName("org.h2.Driver");
            java.net.URL loc = h2.getProtectionDomain().getCodeSource().getLocation();
            return new java.io.File(loc.toURI()).getName();   // e.g. h2-1.4.200-0.cgr.3.jar
        } catch (Exception e) {
            return "h2 (unknown jar)";
        }
    }

    public static void main(String[] args) throws Exception {
        final String h2Jar = h2Coordinate();
        // One in-memory H2 connection kept alive for the app's lifetime.
        Connection conn = DriverManager.getConnection("jdbc:h2:mem:demo;DB_CLOSE_DELAY=-1");
        try (Statement s = conn.createStatement()) {
            s.execute("CREATE TABLE t (id INT PRIMARY KEY, name VARCHAR(50))");
            s.execute("INSERT INTO t VALUES (1, 'Chainguard')");
        }

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8080), 0);
        server.createContext("/", exchange -> {
            String body;
            try (Statement s = conn.createStatement();
                 ResultSet rs = s.executeQuery("SELECT name FROM t WHERE id = 1")) {
                rs.next();
                body = "Chainguard Libraries demo\n"
                     + "backed by " + h2Jar + "\n"
                     + "h2 driver reports: " + conn.getMetaData().getDriverVersion() + "\n"
                     + "query result: " + rs.getString(1) + "\n";
            } catch (Exception e) {
                body = "error: " + e.getMessage() + "\n";
            }
            byte[] out = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
        });
        server.start();
        System.out.println("serving on :8080  (" + h2Jar + ")");
    }
}
