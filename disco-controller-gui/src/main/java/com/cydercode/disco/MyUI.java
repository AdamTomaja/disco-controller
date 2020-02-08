package com.cydercode.disco;

import com.google.common.collect.ImmutableMap;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.external.org.slf4j.Logger;
import com.vaadin.external.org.slf4j.LoggerFactory;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.shared.ui.colorpicker.Color;
import com.vaadin.ui.ColorPicker;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.Slider;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;

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

        ColorPicker stroboColorPicker = new ColorPicker();
        stroboColorPicker.setCaption("Strobo color");
        stroboColorPicker.addValueChangeListener(e -> {
            Color color = e.getValue();
            trySendCommand(ImmutableMap.of("cmd", "setStroboColor", "r", color.getRed(), "g", color.getGreen(), "b", color.getBlue()));
        });

        ComboBox<String> modeSelector = new ComboBox<>();
        modeSelector.setItems("strobo", "noop");
        modeSelector.setCaption("Mode");
        modeSelector.addValueChangeListener(e -> {
            String value = e.getValue();
            ImmutableMap<String, Object> command = ImmutableMap.of("cmd", "setMode", "mode", value);
            trySendCommand(command);
        });
        layout.addComponents(intervalSlider, stroboColorPicker, modeSelector);

        setContent(layout);
    }

    private void trySendCommand(ImmutableMap<String, Object> command) {
        try {
            DiscoClient.getInstance().sendCommandToAll(command);
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
