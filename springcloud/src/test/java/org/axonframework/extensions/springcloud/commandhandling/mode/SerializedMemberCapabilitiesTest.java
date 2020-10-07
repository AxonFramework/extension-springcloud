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

    private SerializedMemberCapabilities testSubject;

    private final Serializer serializer = JacksonSerializer.defaultSerializer();

    @Test
    void testBuildSerializedMemberCapabilitiesThroughDelegate() {
        MemberCapabilities delegate = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER);

        testSubject = new SerializedMemberCapabilities(delegate, serializer);
        testSubject.setSerializer(serializer);

        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }

    @Test
    void testBuildSerializedMemberCapabilitiesThroughFactorAndFilter() {
        testSubject = new SerializedMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER, serializer);
        testSubject.setSerializer(serializer);

        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }

    @Test
    void testBuildSerializedMemberCapabilitiesThroughFactorAndSerializedFilterObject() {
        SerializedObject<String> serializedFilter = serializer.serialize(COMMAND_FILTER, String.class);

        testSubject = new SerializedMemberCapabilities(LOAD_FACTOR, serializedFilter);
        testSubject.setSerializer(serializer);

        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }

    @Test
    void testBuildSerializedMemberCapabilitiesThroughFactorAndSerializedFilter() {
        SerializedObject<String> serializedFilter = serializer.serialize(COMMAND_FILTER, String.class);

        testSubject = new SerializedMemberCapabilities(
                LOAD_FACTOR, serializedFilter.getData(), serializedFilter.getType().getName()
        );
        testSubject.setSerializer(serializer);

        assertEquals(LOAD_FACTOR, testSubject.getLoadFactor());
        assertEquals(COMMAND_FILTER, testSubject.getCommandFilter());
    }

    @Test
    void testGetCommandFilterThrowsIllegalStateExceptionIfNoSerializerIsPresent() {
        MemberCapabilities delegate = new DefaultMemberCapabilities(LOAD_FACTOR, COMMAND_FILTER);

        testSubject = new SerializedMemberCapabilities(delegate, serializer);

        assertThrows(IllegalStateException.class, () -> testSubject.getCommandFilter());
    }
}