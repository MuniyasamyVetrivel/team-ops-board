package com.teamops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TeamOpsApplication {

	public static void main(String[] args) {
		SpringApplication.run(TeamOpsApplication.class, args);
	}

}
