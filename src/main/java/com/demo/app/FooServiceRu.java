package com.demo.app;

import com.demo.lib.IfRegion;
import org.springframework.stereotype.Service;

/**
 * A specific service delegate.
 */
@Service
@IfRegion({"RU"})
public class FooServiceRu implements FooService {

    @Override
    public String getSome() {
        return "a value from " + this.getClass().getName();
    }
}
