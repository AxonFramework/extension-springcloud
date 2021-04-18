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

import org.axonframework.eventhandling.*;
import org.axonframework.extensions.springcloud.eventhandling.configuration.MessagePublisherTestConfiguration;
import org.axonframework.extensions.springcloud.eventhandling.configuration.PublisherTestChannels;
import org.axonframework.extensions.springcloud.eventhandling.converter.DefaultSpringMessageConverter;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.test.binder.MessageCollector;
import org.springframework.messaging.Message;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link SpringStreamMessagePublisher}.
 *
 * @author Mehdi Chitforoosh
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MessagePublisherTestConfiguration.class)
@DirtiesContext
class SpringStreamMessagePublisherTest {

    @Autowired
    private PublisherTestChannels testChannels;

    @Autowired
    private MessageCollector messageCollector;

    private EventBus eventBus;

    private Serializer serializer;
    private SpringMessageConverter converter;

    private SpringStreamMessagePublisher testSubject;

    @BeforeEach
    void setUp() {
        eventBus = SimpleEventBus.builder().build();
        serializer = mock(Serializer.class);
        converter = spy(DefaultSpringMessageConverter.builder()
                .serializer(serializer)
                .build());
        testSubject = new SpringStreamMessagePublisher.Builder()
                .messageSource(eventBus)
                .converter(converter)
                .build();
        testSubject.setOutputChannel(testChannels.input());
    }

    @Test
    void testSendOneMessage() {
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        SimpleSerializedObject<?> testObject =
                new SimpleSerializedObject(payload, payload.getClass(), payload.getClass().getName(), "0");
        when(serializer.serialize(payload, payload.getClass())).thenAnswer(invocationOnMock -> testObject);

        eventBus.publish(eventMessage);
        // Messages emitted by the Spring Stream Cloud test support in a messageCollector
        Message<?> message = messageCollector.forChannel(testChannels.output()).poll();

        assertEquals(payload, message.getPayload());
        assertEquals(eventMessage.getIdentifier(), message.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(eventMessage.getPayloadType().getName(), message.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", message.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(eventMessage.getTimestamp()), message.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", message.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        verify(converter, times(1)).createSpringMessage(any(EventMessage.class));
    }

    @Test
    void testOneDomainEventMessage() {
        String payload = "testMessage";
        GenericDomainEventMessage<Object> domainEventMessage =
                new GenericDomainEventMessage<>(payload.getClass().getName(), "testId", 1L, payload, MetaData.with("key", "value"));

        SimpleSerializedObject<?> testObject =
                new SimpleSerializedObject(payload, payload.getClass(), payload.getClass().getName(), "0");
        when(serializer.serialize(payload, payload.getClass())).thenAnswer(invocationOnMock -> testObject);

        eventBus.publish(domainEventMessage);
        // Messages emitted by the Spring Stream Cloud test support in a messageCollector
        Message<?> messageContainsDomainEventMessage = messageCollector.forChannel(testChannels.output()).poll();

        assertEquals(payload, messageContainsDomainEventMessage.getPayload());
        assertEquals(domainEventMessage.getIdentifier(), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(domainEventMessage.getPayloadType().getName(), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(domainEventMessage.getTimestamp()), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));
        assertEquals(((DomainEventMessage) domainEventMessage).getAggregateIdentifier(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_ID));
        assertEquals(((DomainEventMessage) domainEventMessage).getSequenceNumber(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_SEQ));
        assertEquals(((DomainEventMessage) domainEventMessage).getType(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_TYPE));

        verify(converter, times(1)).createSpringMessage(any(EventMessage.class));
    }

    @Test
    void testSendMultipleMessages() {
        // Given
        String firstPayload = "testMessage1";
        String secondPayload = "testMessage2";
        String thirdPayload = "testMessage3";
        EventMessage<?> firstEventMessage = GenericEventMessage.asEventMessage(firstPayload)
                .withMetaData(MetaData.with("key", "value"));
        EventMessage<?> secondEventMessage = GenericEventMessage.asEventMessage(secondPayload)
                .withMetaData(MetaData.with("key", "value"));
        EventMessage<?> thirdEventMessage = GenericEventMessage.asEventMessage(thirdPayload)
                .withMetaData(MetaData.with("key", "value"));
        List<EventMessage<?>> list = new ArrayList<>(Arrays.asList(firstEventMessage, secondEventMessage, thirdEventMessage));

        SimpleSerializedObject<?> firstTestObject =
                new SimpleSerializedObject(firstPayload, firstPayload.getClass(), firstPayload.getClass().getName(), "0");
        SimpleSerializedObject<?> secondTestObject =
                new SimpleSerializedObject(secondPayload, secondPayload.getClass(), secondPayload.getClass().getName(), "0");
        SimpleSerializedObject<?> thirdTestObject =
                new SimpleSerializedObject(thirdPayload, thirdPayload.getClass(), thirdPayload.getClass().getName(), "0");
        when(serializer.serialize(anyString(), any())).thenAnswer(invocationOnMock -> {
            String arg0 = invocationOnMock.getArgument(0);
            if (arg0.equals("testMessage1")) {
                return firstTestObject;
            } else if (arg0.equals("testMessage2")) {
                return secondTestObject;
            } else {
                return thirdTestObject;
            }
        });

        // When
        eventBus.publish(list);
        // Messages emitted by the Spring Stream Cloud test support in a messageCollector
        Message<?> firstMessage = messageCollector.forChannel(testChannels.output()).poll();
        Message<?> secondMessage = messageCollector.forChannel(testChannels.output()).poll();
        Message<?> thirdMessage = messageCollector.forChannel(testChannels.output()).poll();

        // Then
        assertEquals(firstPayload, firstMessage.getPayload());
        assertEquals(firstEventMessage.getIdentifier(), firstMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(firstEventMessage.getPayloadType().getName(), firstMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", firstMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(firstEventMessage.getTimestamp()), firstMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", firstMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        assertEquals(secondPayload, secondMessage.getPayload());
        assertEquals(secondEventMessage.getIdentifier(), secondMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(secondEventMessage.getPayloadType().getName(), secondMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", secondMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(secondEventMessage.getTimestamp()), secondMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", secondMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        assertEquals(thirdPayload, thirdMessage.getPayload());
        assertEquals(thirdEventMessage.getIdentifier(), thirdMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(thirdEventMessage.getPayloadType().getName(), thirdMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", thirdMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(thirdEventMessage.getTimestamp()), thirdMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", thirdMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        verify(converter, times(3)).createSpringMessage(any(EventMessage.class));
    }

    @Test
    void testSendFilteredMessages() {
        String firstPayload = "testMessage1";
        String secondPayload = "testMessage2";
        String thirdPayload = "testMessage3";
        EventMessage<?> firstEventMessage = GenericEventMessage.asEventMessage(firstPayload)
                .withMetaData(MetaData.with("key", "value"));
        EventMessage<?> secondEventMessage = GenericEventMessage.asEventMessage(secondPayload)
                .withMetaData(MetaData.with("key", "value"));
        EventMessage<?> thirdEventMessage = GenericEventMessage.asEventMessage(thirdPayload)
                .withMetaData(MetaData.with("key", "value"));
        List<EventMessage<?>> list = new ArrayList<>(Arrays.asList(firstEventMessage, secondEventMessage, thirdEventMessage));

        SimpleSerializedObject<?> firstSerializedObject =
                new SimpleSerializedObject(firstPayload, firstPayload.getClass(), firstPayload.getClass().getName(), "0");
        SimpleSerializedObject<?> secondSerializedObject =
                new SimpleSerializedObject(secondPayload, secondPayload.getClass(), secondPayload.getClass().getName(), "0");
        SimpleSerializedObject<?> thirdSerializedObject =
                new SimpleSerializedObject(thirdPayload, thirdPayload.getClass(), thirdPayload.getClass().getName(), "0");
        when(serializer.serialize(anyString(), any())).thenAnswer(invocationOnMock -> {
            String arg0 = invocationOnMock.getArgument(0);
            if (arg0.equals("testMessage1")) {
                return firstSerializedObject;
            } else if (arg0.equals("testMessage2")) {
                return secondSerializedObject;
            } else {
                return thirdSerializedObject;
            }
        });
        //Set filter to remove second event message
        testSubject.setFilter(eventMessage -> !eventMessage.getPayload().equals("testMessage2"));

        eventBus.publish(list);
        // Messages emitted by the Spring Stream Cloud test support in a messageCollector
        Message<?> firstMessage = messageCollector.forChannel(testChannels.output()).poll();
        Message<?> secondMessage = messageCollector.forChannel(testChannels.output()).poll();
        Message<?> thirdMessage = messageCollector.forChannel(testChannels.output()).poll();

        assertEquals(firstPayload, firstMessage.getPayload());
        assertEquals(firstEventMessage.getIdentifier(), firstMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(firstEventMessage.getPayloadType().getName(), firstMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", firstMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(firstEventMessage.getTimestamp()), firstMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", firstMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        assertEquals(thirdPayload, secondMessage.getPayload());
        assertEquals(thirdEventMessage.getIdentifier(), secondMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(thirdEventMessage.getPayloadType().getName(), secondMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals("0", secondMessage.getHeaders().get(Headers.MESSAGE_REVISION));
        assertEquals(formatInstant(thirdEventMessage.getTimestamp()), secondMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", secondMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        assertNull(thirdMessage);

        verify(converter, times(2)).createSpringMessage(any(EventMessage.class));
    }

}
