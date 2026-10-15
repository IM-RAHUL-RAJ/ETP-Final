package com.capstone.etp.automation;

/** The bearer token to present to the Trade REST API for orders on one account. */
public interface TokenSource {

    String tokenFor(Long accountId);
}
