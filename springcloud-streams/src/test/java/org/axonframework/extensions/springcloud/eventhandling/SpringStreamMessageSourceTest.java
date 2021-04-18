/*
 * Copyright (c) 2010-2021. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.springcloud.eventhandling;

import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.extensions.springcloud.eventhandling.configuration.MessageSourceTestConfiguration;
import org.axonframework.extensions.springcloud.eventhandling.configuration.SourceTestChannels;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.utils.TestSerializer;
import org.axonframework.messaging.Headers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringStreamMessageSource}.
 *
 * @author Mehdi Chitforoosh
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MessageSourceTestConfiguration.class)
@DirtiesContext
class SpringStreamMessageSourceTest {

    @Autowired
    private SourceTestChannels testChannels;

    private SpringMessageConverter converter;

    private SpringStreamMessageSource testSubject;

    @BeforeEach
    void setUp() {
        converter = DefaultSpringMessageConverter.builder()
                .serializer(TestSerializer.secureXStreamSerializer())
                .build();
        testSubject = SpringStreamMessageSource.builder()
                .converter(converter)
                .build();
        testChannels.input().subscribe(testSubject);
    }

    @AfterEach
    void clean() {
        testChannels.input().unsubscribe(testSubject);
    }

    @Test
    void testMessageListenerInvokesAllEventProcessors() {
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload);

        testSubject.subscribe(eventProcessor);
        Message<?> message = converter.createSpringMessage(eventMessage);

        testChannels.output().send(message);

        verify(eventProcessor).accept(argThat(item -> item.size() == 1 && item.get(0).getPayload().equals(payload)));
    }

    @Test
    void testMessageListenerIgnoredOnUnsupportedMessageType() {
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload);

        testSubject.subscribe(eventProcessor);
        Message<?> message = converter.createSpringMessage(eventMessage);

        Message<?> messageWithoutHeader = MessageBuilder.fromMessage(message)
                .removeHeader(Headers.MESSAGE_TYPE)
                .build();

        testChannels.output().send(messageWithoutHeader);

        verify(eventProcessor, never()).accept(any(List.class));
    }

    @Test
    void testMessageListenerInvokedOnUnknownSerializedType() {
        Consumer<List<? extends EventMessage<?>>> eventProcessor = mock(Consumer.class);
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload);

        testSubject.subscribe(eventProcessor);
        Message<?> message = converter.createSpringMessage(eventMessage);

        Message<?> messageWithoutHeader = MessageBuilder.fromMessage(message)
                .setHeader(Headers.MESSAGE_TYPE, "unknown")
                .build();

        testChannels.output().send(messageWithoutHeader);

        verify(eventProcessor).accept(any(List.class));
    }
}
