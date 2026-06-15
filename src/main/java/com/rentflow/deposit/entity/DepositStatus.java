package com.rentflow.deposit.entity;

public enum DepositStatus {
    NOT_REQUIRED,
    PENDING_AUTHORIZATION,
    HELD,
    PARTIALLY_DEDUCTED,
    RELEASED,
    FAILED,
    EXPIRED
}
