package com.capstone.etp.kafka.consumer;

import com.capstone.etp.dto.MarketQuote;


public interface QuoteObserver {

    void onQuote(MarketQuote quote);
}