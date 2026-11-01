package com.xenon.qldapm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class QldapmApplication {
	static {
		java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
	}

	public static void main(String[] args) {
		SpringApplication.run(QldapmApplication.class, args);
	}

}
