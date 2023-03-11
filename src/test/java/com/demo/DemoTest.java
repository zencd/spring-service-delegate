package com.demo;

import com.demo.app.FooService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class DemoTest {

	@Autowired
	FooService fooService;

	@Test
	void serviceDelegatedProperly() {
		assertEquals("a value from com.demo.app.FooServiceOld", fooService.getSome());
	}
}
