package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.integration.annotation.Transformer;


@SpringBootApplication
@EnableBinding(Processor.class)
public class MessageProcessorTestConfiguration {

    @Transformer(inputChannel = Processor.OUTPUT, outputChannel = Processor.INPUT)
    public Object transformAsIs(Object test) {
        return test;
    }

}
