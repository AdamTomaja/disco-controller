package com.cydercode.disco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Map;
import java.util.UUID;

public class DiscoClient {

    private static final String host = "localhost";

    private static DiscoClient discoClient;

    private final IMqttClient publisher;

    private DiscoClient() throws MqttException {
        String publisherId = UUID.randomUUID().toString();
        IMqttClient publisher = new MqttClient("tcp://" + host + ":1883", publisherId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        publisher.connect(options);
        this.publisher = publisher;
    }

    public static DiscoClient getInstance() throws MqttException {
        if(discoClient == null) {
            discoClient = new DiscoClient();
        }

        return discoClient;
    }

    public void sendCommandToAll(Map<String, Object> command) throws MqttException, JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        String message = objectMapper.writeValueAsString(command);
        publisher.publish("inTopic", new MqttMessage(message.getBytes()));
    }
}
