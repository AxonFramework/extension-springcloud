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

import org.axonframework.common.AxonConfigurationException;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.extensions.springcloud.eventhandling.converter.SpringMessageConverter;
import org.axonframework.messaging.SubscribableMessageSource;
import org.springframework.integration.endpoint.MessageProducerSupport;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.GenericMessage;

import java.util.List;
import java.util.function.Predicate;

import static org.axonframework.common.BuilderUtils.assertNonNull;

/**
 * Initialize an adapter to forward messages from the given {@link SubscribableMessageSource} to the given {@link MessageChannel}.
 * Messages are not filtered by default; all messages are forwarded to the MessageChannel.
 *
 * @author Mehdi Chitforoosh
 * @since 4.5
 */
public class SpringStreamMessagePublisher extends MessageProducerSupport {

    private Predicate<? super EventMessage<?>> filter;
    private final SubscribableMessageSource<EventMessage<?>> messageSource;
    private final SpringMessageConverter converter;

    /**
     * Instantiate a {@link SpringStreamMessagePublisher} based on the fields contained in the {@link SpringStreamMessagePublisher.Builder}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and thus should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     * <p>
     * Will validate that the {@link SubscribableMessageSource} and {@link SpringMessageConverter} are not {@code null}, and will throw an
     * {@link AxonConfigurationException} if for either of them this holds.
     *
     * @param builder the {@link SpringStreamMessagePublisher.Builder} used to instantiate a {@link SpringStreamMessagePublisher} instance
     */
    protected SpringStreamMessagePublisher(SpringStreamMessagePublisher.Builder builder) {
        builder.validate();
        this.filter = builder.filter;
        this.messageSource = builder.messageSource;
        this.converter = builder.converter;
        // Subscribes this event message handler to the message source.
        this.messageSource.subscribe(this::handle);
    }

    /**
     * Instantiate a Builder to be able to create a {@link SpringStreamMessagePublisher}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and as such should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     *
     * @return a Builder to be able to create a {@link SpringStreamMessagePublisher}.
     */
    public static SpringStreamMessagePublisher.Builder builder() {
        return new SpringStreamMessagePublisher.Builder();
    }

    /**
     * If allows by the filter, wraps the given {@link EventMessage} in a {@link GenericMessage} ands sends it to the
     * configured {@link MessageChannel}.
     *
     * @param events the event messages to handle
     */
    protected void handle(List<? extends EventMessage<?>> events) {
        events.stream()
                .filter(this.filter)
                .forEach(event -> this.sendMessage(this.converter.createSpringMessage(event)));
    }

    /**
     * Set filter to remove filtered messages
     * @param filter
     */
    public void setFilter(Predicate<? super EventMessage<?>> filter) {
        this.filter = filter;
    }

    /**
     * Builder class to instantiate a {@link SpringStreamMessagePublisher}.
     * The {@link SubscribableMessageSource} is a <b>hard requirement</b> and thus should be provided.
     * The {@link SpringMessageConverter} is a <b>hard requirement</b> and thus should be provided.
     */
    public static class Builder {

        private Predicate<? super EventMessage<?>> filter = m -> true;
        private SubscribableMessageSource<EventMessage<?>> messageSource;
        private SpringMessageConverter converter;

        /**
         * Sets the filter to send specific event messages.
         *
         * @param filter The filter to send filtered event messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessagePublisher.Builder filter(Predicate<? super EventMessage<?>> filter) {
            this.filter = filter;
            return this;
        }

        /**
         * Sets the messageSource for subscribing handler .
         *
         * @param messageSource The messageSource to subscribe
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessagePublisher.Builder messageSource(SubscribableMessageSource<EventMessage<?>> messageSource) {
            assertNonNull(messageSource, "messageSource may not be null");
            this.messageSource = messageSource;
            return this;
        }

        /**
         * Sets the converter to convert spring messages to event messages and versa .
         *
         * @param converter The converter to convert messages
         * @return the current Builder instance, for fluent interfacing
         */
        public SpringStreamMessagePublisher.Builder converter(SpringMessageConverter converter) {
            assertNonNull(converter, "converter may not be null");
            this.converter = converter;
            return this;
        }

        /**
         * Initializes a {@link SpringStreamMessagePublisher} as specified through this Builder.
         *
         * @return a {@link SpringStreamMessagePublisher} as specified through this Builder
         */
        public SpringStreamMessagePublisher build() {
            return new SpringStreamMessagePublisher(this);
        }

        /**
         * Validate whether the fields contained in this Builder as set accordingly.
         *
         * @throws AxonConfigurationException if one field is asserted to be incorrect according to the Builder's
         *                                    specifications
         */
        protected void validate() {
            assertNonNull(messageSource, "The MessageSource is a hard requirement and should be provided");
            assertNonNull(converter, "The Converter is a hard requirement and should be provided");
        }
    }

}
