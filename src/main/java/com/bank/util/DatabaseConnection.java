package com.bank.util;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Centralized connection factory. Every DAO calls getConnection() from here
 * instead of hardcoding JDBC URLs — this is the Single Responsibility
 * Principle in action: one class owns "how do we talk to the DB".
 *
 * Note: we deliberately do NOT implement this as a classic Singleton holding
 * one shared Connection. A single shared Connection is not thread-safe and
 * becomes a bottleneck/single point of failure. Instead, each call opens a
 * fresh Connection; in a production system this would be swapped for a
 * connection pool (HikariCP), but a raw factory method is the right level
 * of complexity for a console app and is easy to explain in an interview.
 */
public class DatabaseConnection {

    // Credentials are now externalized into config.properties (not committed
    // to Git) instead of being hardcoded — this is standard practice so
    // secrets never end up in version control.
    private static final Properties props = new Properties();

    static {
        try (InputStream input = DatabaseConnection.class
                .getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException(
                    "config.properties not found on classpath. " +
                    "Create src/main/resources/config.properties with db.url, db.username, db.password."
                );
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.properties", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        // Modern JDBC (4.0+) auto-loads the driver via SPI (META-INF/services),
        // so Class.forName("com.mysql.cj.jdbc.Driver") is no longer required —
        // but knowing it's not required (and why) is itself an interview point.
        String url = props.getProperty("db.url");
        String user = props.getProperty("db.username");
        String password = props.getProperty("db.password");
        return DriverManager.getConnection(url, user, password);
    }
}