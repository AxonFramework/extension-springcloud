package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.extensions.springcloud.eventhandling.SpringStreamMessagePublisher;
import org.axonframework.extensions.springcloud.eventhandling.SpringStreamMessageSource;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.utils.TestSerializer;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.annotation.Transformer;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.dsl.IntegrationFlows;


@SpringBootApplication
@EnableBinding(SourceAndPublisherTestChannels.class)
public class MessageSourceAndPublisherTestConfiguration {

    @Bean
    public EventBus eventBus() {
        return SimpleEventBus.builder()
                .build();
    }

    @Bean
    public SpringMessageConverter converter() {
        return DefaultSpringMessageConverter.builder()
                .serializer(TestSerializer.secureXStreamSerializer())
                .build();
    }

    @Bean
    public SpringStreamMessageSource source(EventBus eventBus, SpringMessageConverter converter) {
        return SpringStreamMessageSource.builder()
                // Optional
//                .eventBus(eventBus)
                .converter(converter)
                .build();
    }

    @Bean
    public SpringStreamMessagePublisher publisher(EventBus eventBus, SpringMessageConverter converter) {
        return SpringStreamMessagePublisher.builder()
                .messageSource(eventBus)
                .converter(converter)
                .build();
    }

    @Bean
    public IntegrationFlow publisherFlow(SpringStreamMessagePublisher publisher) {
        return IntegrationFlows.from(publisher)
                .channel(SourceAndPublisherTestChannels.OUTPUT)
                .get();
    }

    @Bean
    public IntegrationFlow sourceFlow(SpringStreamMessageSource source) {
        return IntegrationFlows.from(SourceAndPublisherTestChannels.INPUT)
                .handle(source)
                .get();
    }

    @Transformer(inputChannel = SourceAndPublisherTestChannels.OUTPUT, outputChannel = SourceAndPublisherTestChannels.INPUT)
    public Object transformAsIs(Object test) {
        return test;
    }

}
