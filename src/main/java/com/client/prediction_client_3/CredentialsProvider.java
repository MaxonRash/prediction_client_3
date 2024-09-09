package com.client.prediction_client_3;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@PropertySource("classpath:credentials.properties")

public class CredentialsProvider {
    public static String USER;
    public static String PASSWORD;
    @Value("${user}")
    public void setUSERNAME(String USER) {
        CredentialsProvider.USER = USER;
    }

    @Value("${password}")
    public void setPASSWORD(String PASSWORD) {
        CredentialsProvider.PASSWORD = PASSWORD;
    }
}
