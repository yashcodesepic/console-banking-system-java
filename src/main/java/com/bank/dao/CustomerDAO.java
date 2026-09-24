package com.bank.dao;

import com.bank.model.Customer;
import com.bank.util.DatabaseConnection;

import java.sql.*;

/**
 * DAO layer: the ONLY place raw SQL is allowed to live.
 * Every method here does exactly one job: talk to the Customers table
 * and translate ResultSets <-> Customer objects.
 * No validation, no business rules — that belongs in the Service layer.
 */
public class CustomerDAO {

    /**
     * Inserts a new customer. Uses PreparedStatement, not Statement +
     * string concatenation — this is what prevents SQL injection, because
     * the '?' placeholders are sent to MySQL separately from the query
     * structure; user input can never be interpreted as SQL syntax.
     */
    public int addCustomer(Customer customer) throws SQLException {
        String sql = "INSERT INTO Customers (name, pin) VALUES (?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             // Statement.RETURN_GENERATED_KEYS lets us retrieve the
             // auto-incremented customer_id MySQL assigns on insert.
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, customer.getName());
            ps.setString(2, customer.getPin());
            ps.executeUpdate();

            // try-with-resources auto-closes conn/ps/rs even if an exception
            // is thrown — no need for a finally block to release DB resources.
            try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1); // the new customer_id
                }
            }
        }
        return -1; // signals failure to caller
    }

    /**
     * Fetches a customer by ID and PIN — used for login authentication.
     * Returns null (not an exception) when no match is found, because
     * "wrong PIN" is an expected outcome, not an error condition.
     */
    public Customer getCustomerByIdAndPin(int customerId, String pin) throws SQLException {
        String sql = "SELECT customer_id, name, pin FROM Customers WHERE customer_id = ? AND pin = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, customerId);
            ps.setString(2, pin);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Customer customer = new Customer();
                    customer.setCustomerId(rs.getInt("customer_id"));
                    customer.setName(rs.getString("name"));
                    customer.setPin(rs.getString("pin"));
                    return customer;
                }
            }
        }
        return null;
    }
}
