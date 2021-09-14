/*
 * Copyright (c) 2010-2021. Axon Framework
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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.DateTimeUtils;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.GenericEventMessage;
import org.axonframework.messaging.Headers;
import org.axonframework.messaging.MetaData;
import org.axonframework.serialization.*;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.GenericMessage;

import java.util.*;

import static org.axonframework.common.BuilderUtils.assertNonNull;
import static org.axonframework.common.DateTimeUtils.formatInstant;
import static org.axonframework.messaging.Headers.MESSAGE_TIMESTAMP;

/**
 * Default implementation of the SpringMessageConverter interface. This implementation will suffice in most cases. It
 * passes all meta-data entries as headers (with 'axon-metadata-' prefix) to the message. Other message-specific
 * attributes are also added as meta data. The message payload is serialized using the configured serializer and passed
 * as the message body.
 *
 * @author Mehdi Chitforoosh
 * @since 4.5
 */
public class DefaultSpringMessageConverter implements SpringMessageConverter {

    private Serializer serializer;

    /**
     * Instantiate a {@link DefaultSpringMessageConverter} based on the fields contained in the {@link Builder}.
     * The {@link Serializer} is a <b>hard requirement</b> and thus should be provided.
     * <p>
     * Will validate that the {@link Serializer} is not {@code null}, and will throw an
     * {@link AxonConfigurationException} if is null.
     *
     * @param builder the {@link Builder} used to instantiate a {@link DefaultSpringMessageConverter} instance
     */
    protected DefaultSpringMessageConverter(Builder builder) {
        builder.validate();
        this.serializer = builder.serializer;
    }

    /**
     * Instantiate a Builder to be able to create a {@link DefaultSpringMessageConverter}.
     * The {@link Serializer} is a <b>hard requirement</b> and as such should be provided.
     *
     * @return a Builder to be able to create a {@link DefaultSpringMessageConverter}.
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Message<?> createSpringMessage(EventMessage<?> eventMessage) {
        if (eventMessage == null || eventMessage.getPayload() == null) {
            throw new NullPointerException("Event message or payload should not be null");
        }
        Object payload = eventMessage.getPayload();
        SerializedObject<?> serializedObject = eventMessage.serializePayload(serializer, payload.getClass());
        Map<String, Object> headers = new HashMap<>();
        eventMessage.getMetaData().forEach((k, v) -> headers.put(Headers.MESSAGE_METADATA + "-" + k, v));
        Headers.defaultHeaders(eventMessage, serializedObject).forEach((k, v) -> {
            if (k.equals(MESSAGE_TIMESTAMP)) {
                headers.put(k, formatInstant(eventMessage.getTimestamp()));
            } else {
                headers.put(k, v);
            }
        });
        return new GenericMessage<>(serializedObject.getData(), new DefaultSpringMessageConverter.SettableTimestampMessageHeaders(headers, eventMessage.getTimestamp().toEpochMilli()));
    }

    @Override
    public Optional<EventMessage<?>> readSpringMessage(Message<?> message) {
        if (message == null) {
            return Optional.empty();
        }
        MessageHeaders headers = message.getHeaders();
        if (!headers.keySet().containsAll(Arrays.asList(Headers.MESSAGE_ID, Headers.MESSAGE_TYPE))) {
            return Optional.empty();
        }
        Object payload = message.getPayload();
        Map<String, Object> metaData = new HashMap<>();
        headers.forEach((k, v) -> {
            if (k.startsWith(Headers.MESSAGE_METADATA + "-")) {
                metaData.put(k.substring((Headers.MESSAGE_METADATA + "-").length()), v);
            }
        });
        SimpleSerializedObject<?> serializedMessage = new SimpleSerializedObject(payload, payload.getClass(),
                Objects.toString(headers.get(Headers.MESSAGE_TYPE)),
                Objects.toString(headers.get(Headers.MESSAGE_REVISION), null));
        SerializedMessage<?> delegateMessage = new SerializedMessage<>(Objects.toString(headers.get(Headers.MESSAGE_ID)),
                new LazyDeserializingObject<>(serializedMessage, serializer),
                new LazyDeserializingObject<>(MetaData.from(metaData)));
        String timestamp = Objects.toString(headers.get(MESSAGE_TIMESTAMP));
        if (headers.containsKey(Headers.AGGREGATE_ID)) {
            return Optional.of(new GenericDomainEventMessage<>(Objects.toString(headers.get(Headers.AGGREGATE_TYPE)),
                    Objects.toString(headers.get(Headers.AGGREGATE_ID)),
                    (Long) headers.get(Headers.AGGREGATE_SEQ),
                    delegateMessage, () -> DateTimeUtils.parseInstant(timestamp)));
        } else {
            return Optional.of(new GenericEventMessage<>(delegateMessage, () -> DateTimeUtils.parseInstant(timestamp)));
        }
    }

    private static class SettableTimestampMessageHeaders extends MessageHeaders {
        protected SettableTimestampMessageHeaders(Map<String, Object> headers, Long timestamp) {
            super(headers, null, timestamp);
        }
    }

    /**
     * Builder class to instantiate a {@link DefaultSpringMessageConverter}.
     * The {@link Serializer} is a <b>hard requirement</b> and thus should be provided.
     */
    public static class Builder {

        private Serializer serializer;

        /**
         * Sets the serializer to serialize the Event Message's payload and Meta Data with.
         *
         * @param serializer The serializer to serialize the Event Message's payload and Meta Data with
         * @return the current Builder instance, for fluent interfacing
         */
        public Builder serializer(Serializer serializer) {
            assertNonNull(serializer, "Serializer may not be null");
            this.serializer = serializer;
            return this;
        }

        /**
         * Initializes a {@link DefaultSpringMessageConverter} as specified through this Builder.
         *
         * @return a {@link DefaultSpringMessageConverter} as specified through this Builder
         */
        public DefaultSpringMessageConverter build() {
            return new DefaultSpringMessageConverter(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(serializer, "The Serializer is a hard requirement and should be provided");
        }
    }
}
