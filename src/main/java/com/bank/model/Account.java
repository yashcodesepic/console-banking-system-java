package com.bank.model;

import java.math.BigDecimal;

/**
 * POJO representing a row in the Accounts table.
 * Uses BigDecimal (not double) for balance — mirrors the DB's DECIMAL type
 * and avoids floating-point rounding errors in currency arithmetic.
 * This is a detail interviewers specifically probe for in FinTech roles.
 */
public class Account {
    private int accountId;
    private int customerId;
    private BigDecimal balance;
    private String accountType;

    public Account() {
    }

    public Account(int accountId, int customerId, BigDecimal balance, String accountType) {
        this.accountId = accountId;
        this.customerId = customerId;
        this.balance = balance;
        this.accountType = accountType;
    }

    public int getAccountId() {
        return accountId;
    }

    public void setAccountId(int accountId) {
        this.accountId = accountId;
    }

    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    @Override
    public String toString() {
        return "Account{accountId=" + accountId + ", balance=" + balance + ", type='" + accountType + "'}";
    }
}
