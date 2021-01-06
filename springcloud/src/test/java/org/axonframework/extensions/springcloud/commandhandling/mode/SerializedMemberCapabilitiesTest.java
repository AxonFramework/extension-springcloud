package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.AcceptAll;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.json.JacksonSerializer;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class validating the {@link SerializedMemberCapabilities}.
 *
 * @author Steven van Beelen
 */
class SerializedMemberCapabilitiesTest {

    private static final int LOAD_FACTOR = 100;
    private static final CommandMessageFilter COMMAND_FILTER = AcceptAll.INSTANCE;

    private final Serializer serializer = JacksonSerializer.defaultSerializer();

    @Test
    void testBuildSerializedMemberCapabilitiesThroughDelegate() {
        SerializedObject<String> testSerializedFilter = serializer.serialize(COMMAND_FILTER, String.class);

        MemberCapabilities delegate = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER);

        SerializedMemberCapabilities testSubject = SerializedMemberCapabilities.build(delegate, serializer);

        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
        assertEquals(testSerializedFilter.getData(), testSubject.getSerializedCommandFilter());
        assertEquals(testSerializedFilter.getType().getName(), testSubject.getSerializedCommandFilterType());
    }
}