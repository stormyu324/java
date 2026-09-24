package com.quant.trading;

public class BrokerNotConfiguredException extends RuntimeException {

    public BrokerNotConfiguredException() {
        super("Alpaca API keys are not configured. Set ALPACA_KEY_ID and ALPACA_SECRET_KEY.");
    }
}
