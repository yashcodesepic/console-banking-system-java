package com.bank.model;

public class Customer {
    private int customerId;
    private String name;
    private String pin;

    // No-arg constructor: needed when the DAO builds an object field-by-field
    // from a ResultSet before all values are known.
    public Customer() {
    }

    // All-args constructor: convenient for creating new Customer objects
    // in the Service layer before persisting them.
    public Customer(int customerId, String name, String pin) {
        this.customerId = customerId;
        this.name = name;
        this.pin = pin;
    }

    // Standard encapsulation: private fields, public getters/setters.
    // This lets us change internal representation later without breaking callers.
    public int getCustomerId() {
        return customerId;
    }

    public void setCustomerId(int customerId) {
        this.customerId = customerId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    // Overriding toString() aids debugging — println(customer) becomes readable.
    @Override
    public String toString() {
        return "Customer{customerId=" + customerId + ", name='" + name + "'}";
        // Deliberately NOT printing the PIN here — even in a demo/toy project,
        // never log or print sensitive credentials. Good habit for interviews.
    }
}

