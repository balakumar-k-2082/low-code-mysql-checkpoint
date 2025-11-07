package com.checkpoint.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a database transaction captured from binlog
 */
public class Transaction {

    private String gtid;                    // Global Transaction ID (if gtid_mode=ON)
    private long xid;                       // XA transaction ID
    private long startPosition;             // Binlog position at transaction start
    private long endPosition;               // Binlog position after transaction end
    private long transactionLength;         // Total bytes (MySQL 8.0.2+)
    private String binlogFilename;
    private boolean committed = false;
    private boolean isDdl = false;          // Is this a DDL transaction?
    private String ddlSql;                  // SQL for DDL transactions

    private List<TransactionEvent> events = new ArrayList<>();

    public void addEvent(TransactionEvent event) {
        event.setSequence(events.size());
        events.add(event);
    }

    public int getEventCount() {
        return events.size();
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    // Getters and setters

    public String getGtid() {
        return gtid;
    }

    public void setGtid(String gtid) {
        this.gtid = gtid;
    }

    public long getXid() {
        return xid;
    }

    public void setXid(long xid) {
        this.xid = xid;
    }

    public long getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(long startPosition) {
        this.startPosition = startPosition;
    }

    public long getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(long endPosition) {
        this.endPosition = endPosition;
    }

    public long getTransactionLength() {
        return transactionLength;
    }

    public void setTransactionLength(long transactionLength) {
        this.transactionLength = transactionLength;
    }

    public String getBinlogFilename() {
        return binlogFilename;
    }

    public void setBinlogFilename(String binlogFilename) {
        this.binlogFilename = binlogFilename;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public boolean isDdl() {
        return isDdl;
    }

    public void setDdl(boolean ddl) {
        isDdl = ddl;
    }

    public String getDdlSql() {
        return ddlSql;
    }

    public void setDdlSql(String ddlSql) {
        this.ddlSql = ddlSql;
    }

    public List<TransactionEvent> getEvents() {
        return events;
    }

    public void setEvents(List<TransactionEvent> events) {
        this.events = events;
    }

    @Override
    public String toString() {
        return "Transaction{" +
               "gtid='" + gtid + '\'' +
               ", xid=" + xid +
               ", events=" + events.size() +
               ", committed=" + committed +
               '}';
    }
}
