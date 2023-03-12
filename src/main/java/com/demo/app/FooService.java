package com.demo.app;

import com.demo.lib.DelegatedService;

/**
 * A service which going to be delegated to {@link FooService1} or {@link FooService2}, basing on runtime info.
 */
@DelegatedService
public interface FooService {
    String getSome();
}
