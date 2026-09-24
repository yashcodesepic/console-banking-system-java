package com.bank.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

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

    // In production these would come from a config file or environment
    // variables, never hardcoded — flagging that awareness matters even
    // in a student project.
    private static final String URL = "jdbc:mysql://localhost:3306/bank_db";
    private static final String USER = "root";
    private static final String PASSWORD = "admin123"; 

    public static Connection getConnection() throws SQLException {
        // Modern JDBC (4.0+) auto-loads the driver via SPI (META-INF/services),
        // so Class.forName("com.mysql.cj.jdbc.Driver") is no longer required —
        // but knowing it's not required (and why) is itself an interview point.
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}