package com.bank.dao;

import com.bank.model.Transaction;
import com.bank.util.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TransactionDAO {

    /**
     * Same pattern as AccountDAO.updateBalance: accepts a caller-supplied
     * Connection so this INSERT participates in the caller's transaction
     * rather than auto-committing on its own.
     */
    public void logTransaction(Connection conn, Transaction transaction) throws SQLException {
        String sql = "INSERT INTO Transactions (account_id, transaction_type, amount) VALUES (?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, transaction.getAccountId());
            // enum.name() converts the enum constant to its String form
            // ("TRANSFER_OUT") for storage — the inverse of Enum.valueOf()
            // used when reading rows back.
            ps.setString(2, transaction.getTransactionType().name());
            ps.setBigDecimal(3, transaction.getAmount());
            ps.executeUpdate();
        }
    }

    public List<Transaction> getTransactionsForAccount(int accountId) throws SQLException {
        String sql = "SELECT transaction_id, account_id, transaction_type, amount, transaction_date " +
                     "FROM Transactions WHERE account_id = ? ORDER BY transaction_date DESC";

        List<Transaction> transactions = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, accountId);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Transaction t = new Transaction();
                    t.setTransactionId(rs.getInt("transaction_id"));
                    t.setAccountId(rs.getInt("account_id"));
                    t.setTransactionType(Transaction.TransactionType.valueOf(rs.getString("transaction_type")));
                    t.setAmount(rs.getBigDecimal("amount"));
                    t.setTransactionDate(rs.getTimestamp("transaction_date").toLocalDateTime());
                    transactions.add(t);
                }
            }
        }
        return transactions;
    }
}
