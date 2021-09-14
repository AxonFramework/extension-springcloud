package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface PublisherTestChannels {

    String OUTPUT = "publisherOutputChannel";

    @Output(OUTPUT)
    MessageChannel output();

    String INPUT = "publisherInputChannel";

    @Input(INPUT)
    SubscribableChannel input();
}
