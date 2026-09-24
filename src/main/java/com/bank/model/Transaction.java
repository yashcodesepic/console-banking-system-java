package com.bank.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * POJO representing a row in the Transactions table.
 * transactionType is modeled as an enum rather than a raw String —
 * this gives compile-time safety (no risk of typo'ing "WITHDRAWL")
 * and makes valid values self-documenting.
 */
public class Transaction {

    // Enum nested inside the model it belongs to — keeps related
    // concepts co-located and avoids polluting the top-level package.
    public enum TransactionType {
        DEPOSIT,
        WITHDRAWAL,
        TRANSFER_IN,
        TRANSFER_OUT
    }

    private int transactionId;
    private int accountId;
    private TransactionType transactionType;
    private BigDecimal amount;
    private LocalDateTime transactionDate;

    public Transaction() {
    }

    public Transaction(int accountId, TransactionType transactionType, BigDecimal amount) {
        // Note: no transactionId or transactionDate here — those are
        // assigned by the database (AUTO_INCREMENT and DEFAULT CURRENT_TIMESTAMP)
        // at insert time, so a "new" transaction shouldn't set them.
        this.accountId = accountId;
        this.transactionType = transactionType;
        this.amount = amount;
    }

    public int getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(int transactionId) {
        this.transactionId = transactionId;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    @Override
    public String toString() {
        return "Transaction{id=" + transactionId + ", type=" + transactionType +
                ", amount=" + amount + ", date=" + transactionDate + "}";
    }
}
