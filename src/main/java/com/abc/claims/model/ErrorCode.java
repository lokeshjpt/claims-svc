package com.abc.claims.model;

public enum ErrorCode {
    E0001("Policy holder does not exist"),
    E0002("Coverage not active on date of service"),
    E0003("Unknown service category"),
    E0004("Future-dated claim rejected"),
    E0005("Missing or malformed claim data");

    private final String message;

    ErrorCode(String message) {
        this.message = message;
    }

    public String message() {
        return message;
    }
}
