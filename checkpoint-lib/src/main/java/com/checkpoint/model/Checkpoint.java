package com.checkpoint.model;

import java.sql.Timestamp;

/**
 * Represents a checkpoint (one per transaction)
 */
public class Checkpoint {

    private long id;
    private String appId;
    private String userId;
    private String name;
    private String description;
    private String actionType;
    private Timestamp createdAt;

    // Transaction identification
    private String gtid;
    private long xid;
    private long transactionLength;

    // Binlog position
    private String binlogFilename;
    private long binlogStartPosition;
    private long binlogEndPosition;

    // Navigation
    private Long parentCheckpointId;

    // Getters and setters

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

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

    public long getBinlogStartPosition() {
        return binlogStartPosition;
    }

    public void setBinlogStartPosition(long binlogStartPosition) {
        this.binlogStartPosition = binlogStartPosition;
    }

    public long getBinlogEndPosition() {
        return binlogEndPosition;
    }

    public void setBinlogEndPosition(long binlogEndPosition) {
        this.binlogEndPosition = binlogEndPosition;
    }

    public Long getParentCheckpointId() {
        return parentCheckpointId;
    }

    public void setParentCheckpointId(Long parentCheckpointId) {
        this.parentCheckpointId = parentCheckpointId;
    }

    @Override
    public String toString() {
        return "Checkpoint{" +
               "id=" + id +
               ", name='" + name + '\'' +
               ", gtid='" + gtid + '\'' +
               ", createdAt=" + createdAt +
               '}';
    }
}
