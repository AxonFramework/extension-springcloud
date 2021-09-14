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

package org.axonframework.extensions.springcloud.eventhandling.converter;

import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.extensions.springcloud.eventhandling.utils.TestSerializer;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.Serializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SpringMessageConverter}.
 *
 * @author Mehdi Chitforoosh
 */
class SpringMessageConverterTest {

    private final Serializer serializer = TestSerializer.secureXStreamSerializer();

    private SpringMessageConverter testSubject;

    @BeforeEach
    void setUp() {
        testSubject = DefaultSpringMessageConverter.builder()
                .serializer(serializer)
                .build();
    }

    @Test
    void testSpringMessageIfIsNull() {
        assertFalse(testSubject.readSpringMessage(null).isPresent());
    }

    @Test
    void testEventMessageIfNotAxonMessageIdPresent() {
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Message<?> message = testSubject.createSpringMessage(eventMessage);
        Message<?> messageWithoutHeader = MessageBuilder.fromMessage(message)
                .removeHeader(Headers.MESSAGE_ID)
                .build();

        assertFalse(testSubject.readSpringMessage(messageWithoutHeader).isPresent());
    }

    @Test
    void testEventMessageIfNotAxonMessageTypePresent() {
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Message<?> message = testSubject.createSpringMessage(eventMessage);
        Message<?> messageWithoutHeader = MessageBuilder.fromMessage(message)
                .removeHeader(Headers.MESSAGE_TYPE)
                .build();

        assertFalse(testSubject.readSpringMessage(messageWithoutHeader).isPresent());
    }

    @Test
    void testPayloadAfterReadingSpringMessage() {
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Message<?> message = testSubject.createSpringMessage(eventMessage);
        EventMessage<?> actualEventMessageResult = testSubject.readSpringMessage(message)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertTrue(actualEventMessageResult.getPayload() instanceof String);
        assertEquals(eventMessage.getPayload(), actualEventMessageResult.getPayload());

        // Test domain event message
        GenericDomainEventMessage<Object> domainEventMessage =
                new GenericDomainEventMessage<>(payload.getClass().getName(), "testId", 1L, payload, MetaData.with("key", "value"));

        Message<?> messageContainsDomainEventMessage = testSubject.createSpringMessage(domainEventMessage);
        EventMessage<?> actualDomainEventMessageResult = testSubject.readSpringMessage(messageContainsDomainEventMessage)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertTrue(actualDomainEventMessageResult.getPayload() instanceof String);
        assertEquals(domainEventMessage.getPayload(), actualDomainEventMessageResult.getPayload());
    }

    @Test
    void testHeadersAndTimestampAfterReadingSpringMessage() {
        // Test event message headers
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Message<?> message = testSubject.createSpringMessage(eventMessage);
        EventMessage<?> actualEventMessageResult = testSubject.readSpringMessage(message)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(eventMessage.getMetaData(), actualEventMessageResult.getMetaData());
        assertEquals(eventMessage.getTimestamp(), actualEventMessageResult.getTimestamp());

        // Test domain event message headers

        GenericDomainEventMessage<Object> domainEventMessage =
                new GenericDomainEventMessage<>(payload.getClass().getName(), "testId", 1L, payload, MetaData.with("key", "value"));

        Message<?> messageContainsDomainEventMessage = testSubject.createSpringMessage(domainEventMessage);
        EventMessage<?> actualDomainEventMessageResult = testSubject.readSpringMessage(messageContainsDomainEventMessage)
                .orElseThrow(() -> new AssertionError("Expected valid message"));

        assertEquals(domainEventMessage.getMetaData(), actualDomainEventMessageResult.getMetaData());
        assertEquals(domainEventMessage.getTimestamp(), actualDomainEventMessageResult.getTimestamp());
    }

    @Test
    void testNullEventMessage() {
        assertThrows(NullPointerException.class, () -> testSubject.createSpringMessage(null));
    }

    @Test
    void testNullEventMessagePayload() {
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(null);

        assertThrows(NullPointerException.class, () -> testSubject.createSpringMessage(eventMessage));
    }

    @Test
    void testHeadersAfterCreatingSpringMessage() {
        // Test event message headers
        String payload = "testMessage";
        EventMessage<?> eventMessage = GenericEventMessage.asEventMessage(payload)
                .withMetaData(MetaData.with("key", "value"));

        Message<?> message = testSubject.createSpringMessage(eventMessage);

        assertEquals(eventMessage.getIdentifier(), message.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(serializer.serialize(payload, payload.getClass()).getType().getName(), message.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals(formatInstant(eventMessage.getTimestamp()), message.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals("value", message.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));

        // Test domain event message headers
        GenericDomainEventMessage<Object> domainEventMessage =
                new GenericDomainEventMessage<>(payload.getClass().getName(), "testId", 1L, payload, MetaData.with("key", "value"));

        Message<?> messageContainsDomainEventMessage = testSubject.createSpringMessage(domainEventMessage);

        assertEquals("value", messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_METADATA + "-" + "key"));
        assertEquals(domainEventMessage.getIdentifier(), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_ID));
        assertEquals(serializer.serialize(payload, payload.getClass()).getType().getName(), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_TYPE));
        assertEquals(formatInstant(domainEventMessage.getTimestamp()), messageContainsDomainEventMessage.getHeaders().get(Headers.MESSAGE_TIMESTAMP));
        assertEquals(((DomainEventMessage) domainEventMessage).getAggregateIdentifier(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_ID));
        assertEquals(((DomainEventMessage) domainEventMessage).getSequenceNumber(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_SEQ));
        assertEquals(((DomainEventMessage) domainEventMessage).getType(), messageContainsDomainEventMessage.getHeaders().get(Headers.AGGREGATE_TYPE));
    }

    @Test
    void testPayloadAfterCreatingSpringMessage() {
        String payload = "testMessage";
        EventMessage<Object> eventMessage = GenericEventMessage.asEventMessage(payload);

        Message message = testSubject.createSpringMessage(eventMessage);

        assertTrue(message.getPayload() instanceof String);

        assertEquals(serializer.serialize(payload, payload.getClass()).getData(), message.getPayload());
    }
}
