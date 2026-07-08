package com.yojanamitra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

@SpringBootApplication
public class ApiApplication {

    public static void main(String[] args) {
        applyDatabaseUrl();
        SpringApplication.run(ApiApplication.class, args);
    }

    /**
     * Support a single {@code DATABASE_URL} connection string (the 12-factor style
     * that Neon / Render / Railway / Heroku provide), e.g.
     * {@code postgresql://user:pass@ep-x.neon.tech/neondb?sslmode=require}.
     * When set, it activates the Postgres profile and fills in the datasource —
     * so no other DB_* vars are needed. Local H2 is unaffected when it's absent.
     */
    private static void applyDatabaseUrl() {
        String raw = System.getenv("DATABASE_URL");
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            URI uri = URI.create(raw.trim().replaceFirst("^postgres(ql)?://", "http://"));
            String user = "";
            String pass = "";
            String userInfo = uri.getUserInfo();
            if (userInfo != null) {
                String[] parts = userInfo.split(":", 2);
                user = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                if (parts.length > 1) {
                    pass = URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
                }
            }
            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String db = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            // Rebuild a clean JDBC URL with only sslmode (drop params pgjdbc rejects, e.g. channel_binding).
            String sslmode = paramOrDefault(uri.getQuery(), "sslmode", "require");
            String jdbc = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + db + "?sslmode=" + sslmode;

            System.setProperty("spring.datasource.url", jdbc);
            if (!user.isEmpty()) {
                System.setProperty("spring.datasource.username", user);
            }
            if (!pass.isEmpty()) {
                System.setProperty("spring.datasource.password", pass);
            }
            System.setProperty("spring.profiles.active", "postgres");
            System.out.println("[YojanaMitra] DATABASE_URL detected -> " + jdbc + " (user=" + user + ")");
        } catch (Exception ex) {
            System.err.println("[YojanaMitra] Could not parse DATABASE_URL: " + ex.getMessage());
        }
    }

    private static String paramOrDefault(String query, String key, String def) {
        if (query == null) {
            return def;
        }
        for (String kv : query.split("&")) {
            int i = kv.indexOf('=');
            if (i > 0 && kv.substring(0, i).equals(key)) {
                return kv.substring(i + 1);
            }
        }
        return def;
    }
}
