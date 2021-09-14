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

import org.axonframework.eventhandling.EventBus;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.eventhandling.SimpleEventBus;
import org.axonframework.extensions.springcloud.eventhandling.configuration.MessageProcessorTestConfiguration;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.utils.TestSerializer;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.messaging.Processor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Test class validating the {@link SpringStreamMessageProcessor}.
 *
 * @author Mehdi Chitforoosh
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MessageProcessorTestConfiguration.class)
@DirtiesContext
class SpringStreamMessageProcessorTest {

    @Autowired
    private Processor testChannels;

    private EventBus eventBus;

    private SpringMessageConverter converter;

    private SpringStreamMessageProcessor testSubject;

    @BeforeEach
    void setUp() {
        eventBus = SimpleEventBus.builder().build();
        converter = DefaultSpringMessageConverter.builder()
                .serializer(TestSerializer.secureXStreamSerializer())
                .build();
        testSubject = SpringStreamMessageProcessor.builder()
                .messageSource(eventBus)
                .converter(converter)
                .build();
        testSubject.setOutputChannel(testChannels.output());
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

        eventBus.publish(eventMessage);

        verify(eventProcessor).accept(argThat(item -> item.size() == 1 && item.get(0).getPayload().equals(payload)));
    }

    @Test
    void testMessageListenerArgument() {
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Consumer<List<? extends EventMessage<?>>> eventProcessor = receivedEventMessages -> {
            EventMessage<?> em = receivedEventMessages.get(0);
            assertEquals(payload, em.getPayload());
            assertEquals(eventMessage.getIdentifier(), em.getIdentifier());
            assertEquals(eventMessage.getTimestamp(), em.getTimestamp());
            assertEquals("value", em.getMetaData().get("key"));
        };

        testSubject.subscribe(eventProcessor);

        eventBus.publish(eventMessage);
    }

}
