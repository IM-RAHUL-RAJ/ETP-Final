package com.capstone.etp.exception;

public abstract class ETPException extends RuntimeException {

    private final String catalogueCode;

    public ETPException(String catalogueCode, String catalogueMessage) {
        super(catalogueMessage);
        this.catalogueCode = catalogueCode;
    }

    public String getCatalogueCode() {
        return catalogueCode;
    }
}