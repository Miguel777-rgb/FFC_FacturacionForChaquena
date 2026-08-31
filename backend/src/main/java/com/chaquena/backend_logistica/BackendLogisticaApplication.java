package com.chaquena.backend_logistica;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // requerido por el worker de outbox
public class BackendLogisticaApplication {

	public static void main(String[] args) {
		SpringApplication.run(BackendLogisticaApplication.class, args);
	}

}
