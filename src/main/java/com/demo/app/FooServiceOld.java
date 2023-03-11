package com.demo.app;

import com.demo.lib.IfBilling;
import org.springframework.stereotype.Service;

/**
 * A specific service delegate.
 */
@Service
@IfBilling({"BILLING1", "BILLING2"})
public class FooServiceOld implements FooService {

    @Override
    public String getSome() {
        return "a value from " + this.getClass().getName();
    }
}
