package com.smartfactory.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Native WebSocket endpoint for React Native / STOMP clients (ws://host:port/ws)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");

        // SockJS fallback endpoint for web browsers
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Topic prefix for server-to-client subscriptions
        registry.enableSimpleBroker("/topic", "/queue");
        // Application prefix for client-to-server messages
        registry.setApplicationDestinationPrefixes("/app");
    }
}
