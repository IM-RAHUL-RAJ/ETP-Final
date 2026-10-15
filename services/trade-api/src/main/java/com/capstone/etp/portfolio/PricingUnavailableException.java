package com.capstone.etp.portfolio;

import com.capstone.etp.exception.ModuleException;
import org.springframework.http.HttpStatus;

public class PricingUnavailableException extends ModuleException {

    public PricingUnavailableException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "MKT-503", "Pricing unavailable");
    }
}
