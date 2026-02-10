package com.zenith.trade;

import com.fnproject.fn.api.FnConfiguration;
import com.fnproject.fn.api.RuntimeContext;

public class HealthCheckFunction {

    @FnConfiguration
    public void config(RuntimeContext ctx) {
        // Configuration logic if needed
    }

    public String handleRequest(String input) {
        return "OK";
    }
}
