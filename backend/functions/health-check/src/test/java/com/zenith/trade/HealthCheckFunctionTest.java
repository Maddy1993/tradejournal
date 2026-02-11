package com.zenith.trade;

import com.fnproject.fn.testing.*;
import org.junit.*;
import static org.junit.Assert.*;

public class HealthCheckFunctionTest {

    @Rule
    public FnTestingRule testing = FnTestingRule.createDefault();

    @Test
    public void shouldReturnOK() {
        testing.givenEvent().enqueue();
        testing.thenRun(HealthCheckFunction.class, "handleRequest");

        FnResult result = testing.getOnlyResult();
        assertEquals("OK", result.getBodyAsString());
    }
}
