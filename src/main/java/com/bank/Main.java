package com.bank;

import com.bank.dao.AccountDAO;
import com.bank.dao.CustomerDAO;
import com.bank.dao.TransactionDAO;
import com.bank.exception.InsufficientFundsException;
import com.bank.model.Customer;
import com.bank.model.Transaction;
import com.bank.service.AccountService;
import com.bank.service.BulkTransferService;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point and presentation layer. Deliberately "dumb" — it only
 * collects input, calls the Service layer, and prints results. No SQL,
 * no business rules live here, keeping the separation of concerns intact
 * all the way up the stack.
 */
public class Main {

    // Single shared Scanner for the app's lifetime — opening multiple
    // Scanners on System.in can cause input stream conflicts.
    private static final Scanner scanner = new Scanner(System.in);

    // Wiring the layers together manually (no DI framework, since we're
    // avoiding Spring). This is "poor man's dependency injection" —
    // worth naming explicitly if asked why there's no framework.
    private static final CustomerDAO customerDAO = new CustomerDAO();
    private static final AccountDAO accountDAO = new AccountDAO();
    private static final TransactionDAO transactionDAO = new TransactionDAO();
    private static final AccountService accountService = new AccountService(accountDAO, transactionDAO);
    private static final BulkTransferService bulkTransferService = new BulkTransferService(accountService);

    // Tracks the logged-in session's active account so menu actions
    // don't need to re-ask for an account ID every time.
    private static int loggedInAccountId = -1;

    public static void main(String[] args) {
    System.out.println("=== Console Banking System ===");

    System.out.println("1. Login");
    System.out.println("2. Register as new customer");
    int startChoice = readIntSafely("Choose an option: ");

    if (startChoice == 2) {
        registerNewCustomer();
    }

    if (!login()) {
        System.out.println("Login failed. Exiting.");
        return;
    }


        boolean running = true;
        while (running) {
            printMenu();
            int choice = readIntSafely("Choose an option: ");

            // switch on int choice, not a switch expression, kept simple
            // and readable for a console app — no need for pattern
            // matching complexity here.
            switch (choice) {
                case 1 -> checkBalance();
                case 2 -> deposit();
                case 3 -> withdraw();
                case 4 -> transferFunds();
                case 5 -> scheduleBulkTransfer();
                case 6 -> processBulkQueue();
                case 7 -> viewTransactionHistory();
                case 8 -> {
                    running = false;
                    System.out.println("Goodbye.");
                }
                default -> System.out.println("Invalid option. Please choose 1-8.");
            }
        }

        scanner.close();
    }

    private static void printMenu() {
        System.out.println("\n--- Menu ---");
        System.out.println("1. Check Balance (cached)");
        System.out.println("2. Deposit");
        System.out.println("3. Withdraw");
        System.out.println("4. Transfer Funds (ACID)");
        System.out.println("5. Schedule Bulk Transfer");
        System.out.println("6. Process Bulk Transfer Queue");
        System.out.println("7. View Transaction History");
        System.out.println("8. Exit");
    }

    /**
     * Login flow. On success, primes the balance cache immediately —
     * this is the moment Requirement #2 (cache populated at login) fires.
     */
    private static void registerNewCustomer() {
    System.out.print("Enter your name: ");
    String name = scanner.nextLine().trim();

    System.out.print("Set a PIN: ");
    String pin = scanner.nextLine().trim();

    try {
        Customer newCustomer = new Customer();
        newCustomer.setName(name);
        newCustomer.setPin(pin);

        int newCustomerId = customerDAO.addCustomer(newCustomer);
        if (newCustomerId == -1) {
            System.out.println("Failed to create customer.");
            return;
        }
        System.out.println("Customer created! Your Customer ID is: " + newCustomerId);

        System.out.print("Enter account type (SAVINGS/CURRENT): ");
        String accountType = scanner.nextLine().trim();

        BigDecimal initialBalance = readBigDecimalSafely("Enter initial deposit amount: ");
        if (initialBalance == null) initialBalance = BigDecimal.ZERO;

        int newAccountId = accountDAO.addAccount(newCustomerId, accountType, initialBalance);
        if (newAccountId == -1) {
            System.out.println("Failed to create account.");
            return;
        }
        System.out.println("Account created! Your Account ID is: " + newAccountId);
        System.out.println("Please log in now using these IDs.");

    } catch (SQLException e) {
        System.out.println("Registration failed: " + e.getMessage());
    }
}
    private static boolean login() {
        int customerId = readIntSafely("Enter Customer ID: ");
        System.out.print("Enter PIN: ");
        String pin = scanner.nextLine().trim();

        try {
            Customer customer = customerDAO.getCustomerByIdAndPin(customerId, pin);
            if (customer == null) {
                System.out.println("Invalid customer ID or PIN.");
                return false;
            }

            int accountId = readIntSafely("Enter Account ID linked to this customer: ");
            accountService.loginAndCacheBalance(accountId); // primes the cache
            loggedInAccountId = accountId;

            System.out.println("Welcome, " + customer.getName() + "!");
            return true;

        } catch (SQLException e) {
            // Never let a raw stack trace be the user-facing error for a
            // DB outage — translate it into an understandable message.
            System.out.println("Database error during login: " + e.getMessage());
            return false;
        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            return false;
        }
    }

    private static void checkBalance() {
        try {
            BigDecimal balance = accountService.getCachedBalance(loggedInAccountId);
            System.out.println("Current balance: " + balance);
        } catch (SQLException e) {
            System.out.println("Could not retrieve balance: " + e.getMessage());
        }
    }

    private static void deposit() {
        BigDecimal amount = readBigDecimalSafely("Enter deposit amount: ");
        if (amount == null) return;

        try {
            accountService.deposit(loggedInAccountId, amount);
            BigDecimal updated = accountService.getCachedBalance(loggedInAccountId);
            System.out.println("Deposit successful. New balance: " + updated);
        } catch (SQLException e) {
            System.out.println("Deposit failed: " + e.getMessage());
        }
    }

    private static void withdraw() {
        BigDecimal amount = readBigDecimalSafely("Enter withdrawal amount: ");
        if (amount == null) return;

        try {
            accountService.withdraw(loggedInAccountId, amount);
            BigDecimal updated = accountService.getCachedBalance(loggedInAccountId);
            System.out.println("Withdrawal successful. New balance: " + updated);
        } catch (InsufficientFundsException e) {
            System.out.println(e.getMessage());
        } catch (SQLException e) {
            System.out.println("Withdrawal failed: " + e.getMessage());
        }
    }

    private static void transferFunds() {
        int toAccountId = readIntSafely("Enter destination Account ID: ");
        BigDecimal amount = readBigDecimalSafely("Enter transfer amount: ");
        if (amount == null) return;

        try {
            accountService.transferFunds(loggedInAccountId, toAccountId, amount);
            System.out.println("Transfer successful.");
        } catch (InsufficientFundsException e) {
            System.out.println("Transfer failed: " + e.getMessage());
        } catch (SQLException e) {
            System.out.println("Transfer failed due to a database error: " + e.getMessage());
        }
    }

    private static void scheduleBulkTransfer() {
        int toAccountId = readIntSafely("Enter destination Account ID: ");
        BigDecimal amount = readBigDecimalSafely("Enter transfer amount: ");
        if (amount == null) return;

        bulkTransferService.scheduleTransfer(loggedInAccountId, toAccountId, amount);
        System.out.println("Transfer queued. Pending transfers: " + bulkTransferService.pendingCount());
    }

    private static void processBulkQueue() {
        if (bulkTransferService.pendingCount() == 0) {
            System.out.println("No pending transfers to process.");
            return;
        }
        System.out.println("Processing " + bulkTransferService.pendingCount() + " queued transfer(s)...");
        bulkTransferService.processQueue();
    }

    private static void viewTransactionHistory() {
        try {
            List<Transaction> history = transactionDAO.getTransactionsForAccount(loggedInAccountId);
            if (history.isEmpty()) {
                System.out.println("No transactions yet.");
                return;
            }
            history.forEach(System.out::println);
        } catch (SQLException e) {
            System.out.println("Could not fetch transaction history: " + e.getMessage());
        }
    }

    // ---------------------------------------------
    // DEFENSIVE INPUT HELPERS (Requirement #5)
    // Every console read goes through one of these, so a stray letter
    // typed where a number is expected can never propagate an unhandled
    // exception up to main() and crash the whole app.
    // ---------------------------------------------

    private static int readIntSafely(String prompt) {
        while (true) {
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                // Loop back and re-prompt instead of propagating the
                // exception — the user gets another attempt, the app
                // never crashes on bad input.
                System.out.println("Invalid number, please try again.");
            }
        }
    }

    /**
     * Returns null (rather than throwing) on repeated bad input after
     * validation, so callers can bail out of the current menu action
     * gracefully instead of looping forever on a confused user.
     */
    private static BigDecimal readBigDecimalSafely(String prompt) {
        System.out.print(prompt);
        String line = scanner.nextLine().trim();
        try {
            BigDecimal value = new BigDecimal(line);
            if (value.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Amount must be positive.");
                return null;
            }
            return value;
        } catch (NumberFormatException e) {
            System.out.println("Invalid amount entered.");
            return null;
        }
    }

    // Placeholder acknowledging that deposit()/withdraw() need a live
    // Connection for the single updateBalance call — in the real project
    // wire this to DatabaseConnection.getConnection() and close it after use,
    // or better: give AccountService dedicated deposit()/withdraw() methods
    // that manage their own connection internally (recommended refactor).
       
}
