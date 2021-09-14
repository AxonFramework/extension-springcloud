package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface SourceAndPublisherTestChannels {

    String OUTPUT = "sourceAndPublisherOutputChannel";

    @Output(OUTPUT)
    MessageChannel output();

    String INPUT = "sourceAndPublisherInputChannel";

    @Input(INPUT)
    SubscribableChannel input();
}
