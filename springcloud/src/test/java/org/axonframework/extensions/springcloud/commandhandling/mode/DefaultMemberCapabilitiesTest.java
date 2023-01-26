/*
 * Copyright (c) 2010-2023. Axon Framework
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link DefaultMemberCapabilities}.
 *
 * @author Steven van Beelen
 */
class DefaultMemberCapabilitiesTest {

    private static final int LOAD_FACTOR = 100;
    private static final CommandMessageFilter COMMAND_FILTER = AcceptAll.INSTANCE;

    private DefaultMemberCapabilities testSubject;

    private final Serializer serializer = JacksonSerializer.defaultSerializer();

    @BeforeEach
    void setUp() {
        testSubject = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER);
    }

    @Test
    void testLoadFactor() {
        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
    }

    @Test
    void testCommandMessageFilter() {
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }

    @Test
    void testConstructDefaultMemberCapabilitiesThroughDelegateAndSerializer() {
        SerializedMemberCapabilities testSerializedCapabilities =
                SerializedMemberCapabilities.build(testSubject, serializer);

        MemberCapabilities deserializedTestSubject =
                new DefaultMemberCapabilities(testSerializedCapabilities, serializer);

        assertEquals(LOAD_FACTOR, deserializedTestSubject.getLoadFactor());
        assertEquals(COMMAND_FILTER, deserializedTestSubject.getCommandFilter());
    }
}