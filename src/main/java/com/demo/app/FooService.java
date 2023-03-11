package com.demo.app;

import com.demo.lib.DelegatedService;

/**
 * A service which going to be delegated to {@link FooServiceOld} or {@link FooServiceNew}, basing on runtime info.
 */
@DelegatedService
public interface FooService {
    String getSome();
}
