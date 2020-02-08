package com.cydercode.disco;

import com.google.common.collect.ImmutableMap;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.external.org.slf4j.Logger;
import com.vaadin.external.org.slf4j.LoggerFactory;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.ui.colorpicker.Color;
import com.vaadin.ui.Button;
import com.vaadin.ui.ColorPicker;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.HorizontalLayout;
import com.vaadin.ui.Slider;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import org.eclipse.paho.client.mqttv3.MqttException;

import javax.servlet.annotation.WebServlet;

/**
 * This UI is the application entry point. A UI may either represent a browser window
 * (or tab) or some part of a html page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be
 * overridden to add component to the user interface and initialize non-component functionality.
 */
@Theme("mytheme")
public class MyUI extends UI {

    private static final Logger logger = LoggerFactory.getLogger(MyUI.class);

    private DiscoClient discoClient;

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();

        HorizontalLayout configurationLayout = new HorizontalLayout();

        TextField hostField = new TextField("Server host");
        hostField.setValue("localhost");

        Button connectButton = new Button("Connect");
        configurationLayout.addComponents(hostField, connectButton);

        Slider intervalSlider = new Slider();
        intervalSlider.setWidth("1000px");
        intervalSlider.setCaption("Animation interval");
        intervalSlider.setMin(10);
        intervalSlider.setMax(1000);
        intervalSlider.addValueChangeListener(e -> {
            trySendCommand(ImmutableMap.of("cmd", "setInterval", "interval", e.getValue()));
        });

        ColorPicker colorAPicker = new ColorPicker("Color A");
        colorAPicker.setCaption("");
        colorAPicker.addValueChangeListener(e -> {
            Color color = e.getValue();
            trySendCommand(ImmutableMap.of("cmd", "setColor", "bank", "A", "r", color.getRed(), "g", color.getGreen(), "b", color.getBlue()));
        });

        ColorPicker colorBPicker = new ColorPicker("Color B");
        colorBPicker.setCaption("");
        colorBPicker.addValueChangeListener(e -> {
            Color color = e.getValue();
            trySendCommand(ImmutableMap.of("cmd", "setColor", "bank", "B", "r", color.getRed(), "g", color.getGreen(), "b", color.getBlue()));
        });

        ComboBox<String> modeSelector = new ComboBox<>();
        modeSelector.setItems("strobo", "static", "noop");
        modeSelector.setCaption("Mode");
        modeSelector.addValueChangeListener(e -> {
            String value = e.getValue();
            ImmutableMap<String, Object> command = ImmutableMap.of("cmd", "setMode", "mode", value);
            trySendCommand(command);
        });
        layout.addComponents(configurationLayout, intervalSlider, colorAPicker, colorBPicker, modeSelector);

        connectButton.addClickListener(e -> {
            try {
                discoClient = new DiscoClient(hostField.getValue());
                discoClient.connect();
                logger.info("Connected");
                configurationLayout.setVisible(false);
            } catch (MqttException ex) {
                logger.error("Unable to connect to mqtt", e);
            }
        });

        setContent(layout);
    }

    private void trySendCommand(ImmutableMap<String, Object> command) {
        try {
            discoClient.sendCommandToAll(command);
            logger.info("Command sent");
        } catch (Exception ex) {
           logger.error("Unable to send command", ex);
        }
    }

    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = MyUI.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }
}
