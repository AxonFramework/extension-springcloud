package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.integration.annotation.Transformer;

@SpringBootApplication
@EnableBinding(SourceTestChannels.class)
public class MessageSourceTestConfiguration {

    @Transformer(inputChannel = SourceTestChannels.OUTPUT, outputChannel = SourceTestChannels.INPUT)
    public Object transformAsIs(Object test) {
        return test;
    }

}
