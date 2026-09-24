package com.bank.service;

import com.bank.exception.InsufficientFundsException;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.Queue;

public class BulkTransferService {

    /**
     * A single scheduled transfer request. Kept as a small immutable-ish
     * record-like class rather than reusing Transaction, because this
     * represents an INTENT to transfer, not a completed ledger entry.
     */
    public static class TransferRequest {
        final int fromAccountId;
        final int toAccountId;
        final BigDecimal amount;

        public TransferRequest(int fromAccountId, int toAccountId, BigDecimal amount) {
            this.fromAccountId = fromAccountId;
            this.toAccountId = toAccountId;
            this.amount = amount;
        }

        @Override
        public String toString() {
            return "Transfer[" + fromAccountId + " -> " + toAccountId + " : " + amount + "]";
        }
    }

    /**
     * QUEUE (Requirement #3).
     * FIFO ordering is deliberate: bulk transfers should be processed in
     * the order they were scheduled — e.g. payroll disbursements that
     * must go out in submission order. LinkedList is used as the concrete
     * implementation because it gives O(1) offer()/poll() at both ends,
     * unlike ArrayList where removing from the front is O(n).
     *
     * Declared as the Queue INTERFACE type, not LinkedList — programming
     * to an interface, not an implementation, so the backing structure
     * could later be swapped (e.g. for ArrayDeque) without changing any
     * calling code.
     */
    private final Queue<TransferRequest> pendingTransfers = new LinkedList<>();

    private final AccountService accountService;

    public BulkTransferService(AccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Adds a transfer to the back of the queue. O(1).
     * Nothing is executed yet — this just schedules intent, mirroring how
     * a real batch-payment system (e.g. end-of-day ACH batch) decouples
     * "submit" from "execute".
     */
    public void scheduleTransfer(int fromAccountId, int toAccountId, BigDecimal amount) {
        pendingTransfers.offer(new TransferRequest(fromAccountId, toAccountId, amount));
    }

    public int pendingCount() {
        return pendingTransfers.size();
    }

    /**
     * Processes the entire queue FIFO, one transfer at a time, via
     * AccountService.transferFunds — so every individual transfer still
     * gets its own full ACID commit/rollback. A failure in ONE queued
     * transfer (e.g. insufficient funds) does NOT abort the whole batch;
     * it's caught, logged, and processing continues with the next item.
     * This models a realistic batch job: partial success is expected and
     * must be reported, not treated as total failure.
     */
    public void processQueue() {
        while (!pendingTransfers.isEmpty()) {
            // poll() removes and returns the head of the queue — O(1),
            // and safely returns null instead of throwing if empty
            // (though the while condition already guards against that).
            TransferRequest request = pendingTransfers.poll();

            try {
                accountService.transferFunds(request.fromAccountId, request.toAccountId, request.amount);
                System.out.println("[SUCCESS] " + request);
            } catch (InsufficientFundsException e) {
                System.out.println("[FAILED - insufficient funds] " + request + " -> " + e.getMessage());
            } catch (SQLException e) {
                System.out.println("[FAILED - DB error] " + request + " -> " + e.getMessage());
            } catch (IllegalArgumentException e) {
                System.out.println("[FAILED - invalid request] " + request + " -> " + e.getMessage());
            }
            // Loop continues regardless of outcome — one bad transfer
            // doesn't block the rest of the batch.
        }
    }
}