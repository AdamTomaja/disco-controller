package com.cydercode.disco;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.vaadin.ui.TextArea;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import org.eclipse.paho.client.mqttv3.MqttException;

import javax.servlet.annotation.WebServlet;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private TextArea sequenceTextArea = new TextArea("Timeline");
    private SequenceExecutor sequenceExecutor;

    {
        sequenceTextArea.setWidth("100%");
        sequenceTextArea.setHeight("1000px");
    }

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        final VerticalLayout layout = new VerticalLayout();

        Slider intervalSlider = new Slider();
        intervalSlider.setWidth("1000px");
        intervalSlider.setCaption("Animation interval");
        intervalSlider.setMin(10);
        intervalSlider.setMax(1000);
        intervalSlider.addValueChangeListener(e -> {
            trySendCommand(ImmutableMap.of("cmd", "setInterval", "interval", e.getValue()));
        });

        ComboBox<String> modeSelector = new ComboBox<>();
        modeSelector.setItems("strobo", "static", "noop");
        modeSelector.setCaption("Mode");
        modeSelector.addValueChangeListener(e -> {
            String value = e.getValue();
            ImmutableMap<String, Object> command = ImmutableMap.of("cmd", "setMode", "mode", value);
            trySendCommand(command);
        });

        HorizontalLayout sequenceButtons = createSequenceButtons();


        layout.addComponents(createConfigurationLayout(),
                intervalSlider,
                createColorsLayout(),
                modeSelector,
                sequenceButtons,
                sequenceTextArea);

        setContent(layout);
    }

    private HorizontalLayout createSequenceButtons() {
        HorizontalLayout sequenceButtons = new HorizontalLayout();
        Button startSequenceButton = new Button("Start sequence");
        Button stopSequenceButton = new Button("Stop sequence");
        stopSequenceButton.setVisible(false);

        sequenceButtons.addComponents(startSequenceButton, stopSequenceButton);

        startSequenceButton.addClickListener(e -> {
            ObjectMapper objectMapper = new ObjectMapper();
            String[] commands = sequenceTextArea.getValue().split("\n");
            List<Map<String, Object>> cmds = Stream.of(commands).map(cmd -> {
                try {
                    return (Map<String, Object>)objectMapper.readValue(cmd, Map.class);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }).collect(Collectors.toList());

            sequenceExecutor = new SequenceExecutor(discoClient, cmds, 300);
            sequenceExecutor.start();
            startSequenceButton.setVisible(false);
            stopSequenceButton.setVisible(true);
        });

        stopSequenceButton.addClickListener(e -> {
            sequenceExecutor.kill();
            startSequenceButton.setVisible(true);
            stopSequenceButton.setVisible(false);
        });
        return sequenceButtons;
    }


    private HorizontalLayout createColorsLayout() {
        HorizontalLayout colorsLayout = new HorizontalLayout();

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

        colorsLayout.addComponents(colorAPicker, colorBPicker);
        return colorsLayout;
    }

    private HorizontalLayout createConfigurationLayout() {
        HorizontalLayout configurationLayout = new HorizontalLayout();

        TextField hostField = new TextField("Server host");
        hostField.setValue("localhost");

        Button connectButton = new Button("Connect");
        configurationLayout.addComponents(hostField, connectButton);

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
        return configurationLayout;
    }

    private void trySendCommand(Map<String, Object> command) {
        try {
            discoClient.sendCommandToAll(command);
            logger.info("Command sent");
            sequenceTextArea.setValue(sequenceTextArea.getValue() + "\n" + new ObjectMapper().writeValueAsString(command));
        } catch (Exception ex) {
           logger.error("Unable to send command", ex);
        }
    }

    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = MyUI.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }
}
