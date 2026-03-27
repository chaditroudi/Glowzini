package com.glowzi.identity;

import org.springframework.boot.SpringApplication;

public class TestGlowziIdentityServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(GlowziIdentityServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
