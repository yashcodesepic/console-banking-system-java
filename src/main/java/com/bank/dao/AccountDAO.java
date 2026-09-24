package com.bank.dao;

import com.bank.model.Account;
import com.bank.util.DatabaseConnection;

import java.math.BigDecimal;
import java.sql.*;

public class AccountDAO {

    /**
 * Creates a new account for an existing customer. Requires a valid
 * customer_id (foreign key) — the customer must already exist in the
 * Customers table, or this INSERT will fail due to the FK constraint.
 */
public int addAccount(int customerId, String accountType, BigDecimal initialBalance) throws SQLException {
    String sql = "INSERT INTO Accounts (customer_id, account_type, balance) VALUES (?, ?, ?)";

    try (Connection conn = DatabaseConnection.getConnection();
         PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

        ps.setInt(1, customerId);
        ps.setString(2, accountType);
        ps.setBigDecimal(3, initialBalance);
        ps.executeUpdate();

        try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1); // the new account_id
            }
        }
    }
    return -1; // signals failure to caller
}

    public Account getAccountById(int accountId) throws SQLException {
        String sql = "SELECT account_id, customer_id, balance, account_type FROM Accounts WHERE account_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToAccount(rs);
                }
            }
        }
        return null;
    }

    /**
     * IMPORTANT: this method accepts an existing Connection instead of opening
     * its own. That's what allows the Service layer to run multiple DAO calls
     * (debit one account, credit another) on the SAME connection/transaction,
     * so COMMIT/ROLLBACK apply to both together. This overload is the one the
     * transfer logic in Step 3 will actually use.
     */
    public void updateBalance(Connection conn, int accountId, BigDecimal newBalance) throws SQLException {
        String sql = "UPDATE Accounts SET balance = ? WHERE account_id = ?";

        // Note: no try-with-resources on `conn` here — this method does NOT
        // own the connection's lifecycle, the caller (Service layer) does.
        // Closing it here would break the caller's transaction.
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBigDecimal(1, newBalance);
            ps.setInt(2, accountId);
            ps.executeUpdate();
        }
    }

    /**
     * Overload used for simple, single-statement balance reads/writes outside
     * of a multi-step transaction (e.g. a plain deposit). Opens its own
     * connection since there's nothing else to coordinate with.
     */
    public Account getAccountById(Connection conn, int accountId) throws SQLException {
        String sql = "SELECT account_id, customer_id, balance, account_type FROM Accounts WHERE account_id = ? FOR UPDATE";
        // FOR UPDATE places a row-level lock on this account's row until the
        // enclosing transaction commits/rolls back — this is what prevents a
        // race condition where two concurrent transfers both read the same
        // stale balance and overdraw the account. Worth explaining unprompted
        // in the interview; it signals you understand concurrency, not just syntax.

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRowToAccount(rs);
                }
            }
        }
        return null;
    }

    private Account mapRowToAccount(ResultSet rs) throws SQLException {
        Account account = new Account();
        account.setAccountId(rs.getInt("account_id"));
        account.setCustomerId(rs.getInt("customer_id"));
        account.setBalance(rs.getBigDecimal("balance"));
        account.setAccountType(rs.getString("account_type"));
        return account;
    }
}
