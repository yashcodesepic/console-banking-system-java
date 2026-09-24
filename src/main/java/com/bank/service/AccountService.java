package com.bank.service;

import com.bank.dao.AccountDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.InsufficientFundsException;
import com.bank.model.Account;
import com.bank.model.Transaction;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Business logic layer. This is where validation, math, caching, and
 * transaction orchestration live — the DAO layer never sees any of this,
 * it only executes SQL handed to it.
 */
public class AccountService {

    private final AccountDAO accountDAO;
    private final TransactionDAO transactionDAO;

    /**
     * IN-MEMORY CACHE (Requirement #2)
     * Key: accountId, Value: cached balance.
     * Populated on login/first access, read on subsequent balance checks
     * within the same session — avoids a redundant SELECT for every
     * "check my balance" menu action. Invalidated (updated) on every
     * write so it never serves stale data back to the same session.
     *
     * Using accountId as the key rather than customerId because balance
     * is a property of the Account, not the Customer (a customer can have
     * multiple accounts) — keying by the wrong entity is a subtle bug
     * worth being able to spot in an interview.
     */
    private final Map<Integer, BigDecimal> balanceCache = new HashMap<>();

    public AccountService(AccountDAO accountDAO, TransactionDAO transactionDAO) {
        this.accountDAO = accountDAO;
        this.transactionDAO = transactionDAO;
    }

    /**
     * Called once at login. Primes the cache so the very next balance
     * check is a HashMap O(1) lookup instead of a DB round-trip.
     */
    public BigDecimal loginAndCacheBalance(int accountId) throws SQLException {
        Account account = accountDAO.getAccountById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("No account found for id " + accountId);
        }
        balanceCache.put(accountId, account.getBalance());
        return account.getBalance();
    }

    /**
     * Cache-first read. Falls back to the DB only on a cache miss
     * (e.g. app restarted, or account was never logged into this session).
     * This is the "reducing redundant SELECT queries" requirement in action.
     */
    public BigDecimal getCachedBalance(int accountId) throws SQLException {
        if (balanceCache.containsKey(accountId)) {
            return balanceCache.get(accountId);
        }
        // Cache miss — go to the source of truth and backfill the cache.
        Account account = accountDAO.getAccountById(accountId);
        if (account == null) {
            throw new IllegalArgumentException("No account found for id " + accountId);
        }
        balanceCache.put(accountId, account.getBalance());
        return account.getBalance();
    }
        /**
     * Deposits funds into an account. Wrapped in its own transaction so the
     * balance UPDATE and the ledger INSERT commit or roll back together,
     * consistent with how transferFunds() is structured.
     */
    public void deposit(int accountId, BigDecimal amount) throws SQLException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be positive");
        }

        Connection conn = null;
        try {
            conn = com.bank.util.DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountDAO.getAccountById(conn, accountId);
            if (account == null) {
                throw new IllegalArgumentException("No account found for id " + accountId);
            }

            BigDecimal newBalance = account.getBalance().add(amount);
            accountDAO.updateBalance(conn, accountId, newBalance);
            transactionDAO.logTransaction(conn,
                new Transaction(accountId, Transaction.TransactionType.DEPOSIT, amount));

            conn.commit();
            balanceCache.put(accountId, newBalance);

        } catch (SQLException | IllegalArgumentException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    /**
     * Withdraws funds from an account. Same atomic pattern as deposit(),
     * with an added InsufficientFundsException check before any write.
     */
    public void withdraw(int accountId, BigDecimal amount) throws SQLException, InsufficientFundsException {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be positive");
        }

        Connection conn = null;
        try {
            conn = com.bank.util.DatabaseConnection.getConnection();
            conn.setAutoCommit(false);

            Account account = accountDAO.getAccountById(conn, accountId);
            if (account == null) {
                throw new IllegalArgumentException("No account found for id " + accountId);
            }

            if (account.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Account " + accountId + " has insufficient funds for a withdrawal of " + amount);
            }

            BigDecimal newBalance = account.getBalance().subtract(amount);
            accountDAO.updateBalance(conn, accountId, newBalance);
            transactionDAO.logTransaction(conn,
                new Transaction(accountId, Transaction.TransactionType.WITHDRAWAL, amount));

            conn.commit();
            balanceCache.put(accountId, newBalance);

        } catch (SQLException | InsufficientFundsException | IllegalArgumentException e) {
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    rollbackEx.printStackTrace();
                }
            }
            throw e;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }

    /**
     * THE CORE ACID TRANSACTION (Requirement #1).
     *
     * Transfers money between two accounts. Both the debit and the credit
     * must succeed together or not at all — this method is the textbook
     * definition of atomicity, and is almost certainly what a FinTech
     * interviewer will ask you to walk through line by line.
     */
    public void transferFunds(int fromAccountId, int toAccountId, BigDecimal amount)
            throws SQLException, InsufficientFundsException {

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive");
        }

        Connection conn = null;
        try {
            conn = com.bank.util.DatabaseConnection.getConnection();

            // Step 1: turn off auto-commit. By default, JDBC commits every
            // single statement immediately. Disabling that is what lets us
            // group the debit + credit + two ledger inserts into one
            // all-or-nothing unit of work.
            conn.setAutoCommit(false);

            // Step 2: read both accounts WITH row locks (FOR UPDATE, defined
            // in AccountDAO). Locking here — inside the transaction, before
            // any writes — is what prevents a second concurrent transfer
            // from reading the same pre-transfer balance (the "lost update"
            // problem in concurrency theory).
            Account fromAccount = accountDAO.getAccountById(conn, fromAccountId);
            Account toAccount = accountDAO.getAccountById(conn, toAccountId);

            if (fromAccount == null || toAccount == null) {
                throw new IllegalArgumentException("One or both accounts do not exist");
            }

            // Step 3: business rule validation BEFORE touching any data.
            // Custom checked exception (Requirement #5) forces the caller
            // to handle the "not enough money" case explicitly.
            if (fromAccount.getBalance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(
                    "Account " + fromAccountId + " has insufficient funds for a transfer of " + amount);
            }

            // Step 4: compute new balances in Java (not in SQL) so the
            // logic is easy to unit-test independently of the database.
            BigDecimal newFromBalance = fromAccount.getBalance().subtract(amount);
            BigDecimal newToBalance = toAccount.getBalance().add(amount);

            // Step 5: perform both writes on the SAME connection/transaction.
            accountDAO.updateBalance(conn, fromAccountId, newFromBalance);
            accountDAO.updateBalance(conn, toAccountId, newToBalance);

            // Step 6: write the audit trail — also part of the same
            // atomic unit. If the ledger insert fails, the balance changes
            // must roll back too, otherwise money moves with no record of it.
            transactionDAO.logTransaction(conn,
                new Transaction(fromAccountId, Transaction.TransactionType.TRANSFER_OUT, amount));
            transactionDAO.logTransaction(conn,
                new Transaction(toAccountId, Transaction.TransactionType.TRANSFER_IN, amount));

            // Step 7: everything succeeded — make it permanent.
            conn.commit();

            // Step 8: only update the cache AFTER commit succeeds, so the
            // cache never reflects a change that the DB ultimately rejected.
            balanceCache.put(fromAccountId, newFromBalance);
            balanceCache.put(toAccountId, newToBalance);

        } catch (SQLException | InsufficientFundsException | IllegalArgumentException e) {
            // Step 9: ANY failure in the try block — a locked row timing out,
            // a constraint violation, insufficient funds — undoes every
            // write made since setAutoCommit(false). This is the rollback
            // half of atomicity; without it, a failed transfer could leave
            // one account debited with no corresponding credit.
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // Rollback itself failing is rare but must not be silently
                    // swallowed — at minimum, surface it.
                    rollbackEx.printStackTrace();
                }
            }
            throw e; // re-throw so the Service caller (Main/menu) can react
        } finally {
            // Step 10: always restore auto-commit and close the connection,
            // regardless of success or failure — resource cleanup must be
            // unconditional.
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException closeEx) {
                    closeEx.printStackTrace();
                }
            }
        }
    }
}


