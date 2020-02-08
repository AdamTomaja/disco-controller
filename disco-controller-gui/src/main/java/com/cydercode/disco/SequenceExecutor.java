package com.cydercode.disco;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.vaadin.external.org.slf4j.Logger;
import com.vaadin.external.org.slf4j.LoggerFactory;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.List;
import java.util.Map;

public class SequenceExecutor extends Thread implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(SequenceExecutor.class);

    private final DiscoClient discoClient;
    private final List<Map<String, Object>> commands;
    private final int interval;

    private volatile boolean running = true;

    public SequenceExecutor(DiscoClient discoClient, List<Map<String, Object>> commands, int interval) {
        this.discoClient = discoClient;
        this.commands = commands;
        this.interval = interval;
    }

    @Override
    public void run() {
        while(running) {
            for(Map<String, Object> command : commands) {
                try {
                    if(!running) {
                        return;
                    }

                    discoClient.sendCommandToAll(command);
                    Thread.sleep(interval);
                } catch (MqttException | JsonProcessingException | InterruptedException e) {
                    logger.error("Cannot send sequence command", e);
                }
            }
        }
    }

    public void kill() {
        running = false;
    }
}
