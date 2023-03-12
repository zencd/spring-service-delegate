package com.demo.app;

import com.demo.lib.IfBilling;
import org.springframework.stereotype.Service;

/**
 * A specific service delegate.
 */
@Service
@IfBilling("BILLING2")
public class FooService2 implements FooService {

    @Override
    public String getSome() {
        return "a value from " + this.getClass().getName();
    }
}
