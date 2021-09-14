package org.axonframework.extensions.springcloud.eventhandling.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.Transformer;

@SpringBootApplication
@EnableBinding(PublisherTestChannels.class)
public class MessagePublisherTestConfiguration {

    // To convert Java 8 Instant Date/Time in event message to Json in Spring message
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule());
        return mapper;
    }

    @Transformer(inputChannel = PublisherTestChannels.INPUT, outputChannel = PublisherTestChannels.OUTPUT)
    public Object transformAsIs(Object test) {
        return test;
    }

}
