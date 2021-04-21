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
import org.axonframework.extensions.springcloud.eventhandling.configuration.MessageSourceAndPublisherTestConfiguration;
import org.axonframework.messaging.MetaData;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Mehdi Chitforoosh
 */
@RunWith(SpringRunner.class)
@SpringBootTest(classes = MessageSourceAndPublisherTestConfiguration.class)
@DirtiesContext
class SpringStreamMessageSourceAndPublisherIntegrationTest {

    @Autowired
    private EventBus eventBus;

    @Autowired
    private SpringStreamMessageSource messageSource;

    @Test
    void testMessageListener() {
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

        messageSource.subscribe(eventProcessor);

        eventBus.publish(eventMessage);
    }

}
