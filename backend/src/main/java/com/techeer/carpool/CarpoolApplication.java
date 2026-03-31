package com.techeer.carpool;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
// sldkfj
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
public class CarpoolApplication {

	public static void main(String[] args) {
		//String client_id = System.getenv("GOOGLE_CLIENT_ID");
		//String client_sk = System.getenv("GOOGLE_CLIENT_SECRET");
		//System.out.println("client_id : " + client_id);
		//System.out.println("client_sk : " + client_sk);

		SpringApplication.run(CarpoolApplication.class, args);
	}

}
