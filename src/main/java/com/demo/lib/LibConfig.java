package com.demo.lib;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Spring config required for this library to work.
 */
@Configuration
@Import(DelegatedServiceBeanRegistrar.class)
public class LibConfig {
    static final String BASE_PACKAGE = "com.demo";
}
