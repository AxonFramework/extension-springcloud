package org.axonframework.extensions.springcloud.eventhandling.configuration;

import org.springframework.cloud.stream.annotation.Input;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.SubscribableChannel;

public interface SourceTestChannels {

    String OUTPUT = "sourceOutputChannel";

    @Output(OUTPUT)
    MessageChannel output();

    String INPUT = "sourceInputChannel";

    @Input(INPUT)
    SubscribableChannel input();
}
