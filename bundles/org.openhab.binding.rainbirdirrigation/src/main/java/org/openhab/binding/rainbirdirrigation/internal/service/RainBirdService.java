package org.openhab.binding.rainbirdirrigation.internal.service;

import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.openhab.binding.rainbirdirrigation.internal.RainBirdIrrigationConfiguration;
import org.openhab.binding.rainbirdirrigation.internal.controller.RainBirdCommand;
import org.openhab.binding.rainbirdirrigation.internal.encryption.AesEncryption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class RainBirdService {
    private final Logger logger = LoggerFactory.getLogger(RainBirdService.class);
    private final RainBirdIrrigationConfiguration configuration;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    public RainBirdService(RainBirdIrrigationConfiguration configuration, HttpClient httpClient) {
        this.configuration = configuration;
        this.httpClient = httpClient;
        this.objectMapper = new ObjectMapper();
    }

    public String callController(RainBirdCommand command)
            throws JsonProcessingException, InvalidAlgorithmParameterException, NoSuchPaddingException,
            IllegalBlockSizeException, NoSuchAlgorithmException, BadPaddingException, InvalidKeySpecException,
            InvalidKeyException, ExecutionException, InterruptedException, TimeoutException {

        ContentResponse response = httpClient.POST(configuration.hostname).timeout(10, TimeUnit.SECONDS)
                .header("Accept-Language", "en").header("Accept-Encoding", "gzip, deflate")
                .header("User-Agent", "RainBird/2.0 CFNetwork/811.5.4 Darwin/16.7.0").header("Accept", "*/*")
                .header("Connection", "keep-alive").header("Content-Type", "application/octet-stream")
                .content(new BytesContentProvider(AesEncryption.encryptWithPassword(
                        objectMapper.writeValueAsString(command.getRequest()), this.configuration.password)))
                .send();

        logger.error("Response Status = {}", response.getStatus());
        return "RESPOSNE";
    }
}
