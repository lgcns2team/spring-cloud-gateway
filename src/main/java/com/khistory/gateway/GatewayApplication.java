package com.khistory.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication(scanBasePackages = "com.khistory.gateway")
public class GatewayApplication {

	public static void main(String[] args) {
		// .env 파일을 읽어서 시스템 프로퍼티로 등록
		Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
		dotenv.entries().forEach(entry -> System.setProperty(entry.getKey(), entry.getValue()));

		SpringApplication.run(GatewayApplication.class, args);
	}

}
