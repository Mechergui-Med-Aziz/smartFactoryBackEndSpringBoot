package com.smartfactory.config;

import com.smartfactory.service.SensorReadingService;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

@Configuration
public class MqttConfig {

    private static final Logger log = LoggerFactory.getLogger(MqttConfig.class);

    @Value("${mqtt.broker-url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client-id:smartfactory-backend}")
    private String clientId;

    @Value("${mqtt.username:}")
    private String username;

    @Value("${mqtt.password:}")
    private String password;

    @Value("${mqtt.topic:factory/machines/#}")
    private String topic;

    @Value("${mqtt.qos:1}")
    private int qos;

    @Value("${mqtt.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});

        if (StringUtils.hasText(username)) {
            options.setUserName(username.trim());
        }
        if (StringUtils.hasText(password)) {
            options.setPassword(password.trim().toCharArray());
        }

        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(60);

        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageChannel mqttInboundChannel() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer mqttInboundAdapter(MqttPahoClientFactory clientFactory) {
        // Appending random suffix to clientId to prevent collisions during re-connections
        String subscriberClientId = clientId + "-sub-" + System.currentTimeMillis();
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(subscriberClientId, clientFactory, topic) {
                    @Override
                    public void destroy() {
                        try {
                            super.destroy();
                        } catch (NullPointerException ignored) {
                            // Safe shutdown when adapter was never started (client not initialized)
                        }
                    }
                };

        adapter.setCompletionTimeout(5000);
        adapter.setConverter(new DefaultPahoMessageConverter());
        adapter.setQos(qos);
        adapter.setOutputChannel(mqttInboundChannel());
        adapter.setAutoStartup(autoStartup);

        log.info("Configured MQTT subscriber for topic [{}] on broker [{}] (auto-startup: {})",
                topic, brokerUrl, autoStartup);
        return adapter;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttInboundChannel")
    public MessageHandler mqttInboundHandler(SensorReadingService readingService) {
        return message -> {
            String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
            Object payloadObj = message.getPayload();
            String payloadStr;

            if (payloadObj instanceof byte[]) {
                payloadStr = new String((byte[]) payloadObj, StandardCharsets.UTF_8);
            } else {
                payloadStr = String.valueOf(payloadObj);
            }

            try {
                readingService.validateAndProcess(topic, payloadStr);
            } catch (Exception ex) {
                // Non-fatal: log error structured and continue receiving subsequent messages
                log.warn("Error processing MQTT message on topic [{}]: {}", topic, ex.getMessage());
            }
        };
    }
}
