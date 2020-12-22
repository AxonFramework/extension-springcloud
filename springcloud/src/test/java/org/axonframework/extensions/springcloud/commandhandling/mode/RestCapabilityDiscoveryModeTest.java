package org.axonframework.extensions.springcloud.commandhandling.mode;

import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.commandhandling.distributed.commandfilter.CommandNameFilter;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.xml.XStreamSerializer;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

import static org.axonframework.extensions.springcloud.commandhandling.mode.DefaultMemberCapabilities.INCAPABLE_MEMBER;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test class validating the {@link RestCapabilityDiscoveryMode}.
 *
 * @author Steven van Beelen
 */
class RestCapabilityDiscoveryModeTest {

    private static final String CUSTOM_ENDPOINT = "/our-custom-endpoint";
    private static final int LOAD_FACTOR = 42;
    private static final CommandMessageFilter COMMAND_MESSAGE_FILTER = new CommandNameFilter("my-command-name");

    private final Serializer serializer = XStreamSerializer.defaultSerializer();
    private final RestTemplate restTemplate = mock(RestTemplate.class);

    private CapabilityDiscoveryMode testSubject;

    private final ServiceInstance localInstance = mock(ServiceInstance.class);
    private final ArgumentCaptor<URI> uriArgumentCaptor = ArgumentCaptor.forClass(URI.class);

    @BeforeEach
    void setUp() {
        testSubject = RestCapabilityDiscoveryMode.builder()
                                                 .serializer(serializer)
                                                 .restTemplate(restTemplate)
                                                 .disableIgnoreListing()
                                                 .messageCapabilitiesEndpoint(CUSTOM_ENDPOINT)
                                                 .build();
    }

    @Test
    void testGetLocalMemberCapabilitiesReturnsIncapableMemberIfLocalCapabilitiesIsNeverUpdated() {
        MemberCapabilities result = ((RestCapabilityDiscoveryMode) testSubject).getLocalMemberCapabilities();
        assertEquals(INCAPABLE_MEMBER, result);
    }

    @Test
    void testGetLocalMemberCapabilitiesReturnsUpdatedLocalCapabilities() {
        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        MemberCapabilities result = ((RestCapabilityDiscoveryMode) testSubject).getLocalMemberCapabilities();

        assertEquals(LOAD_FACTOR, result.getLoadFactor());
        assertEquals(COMMAND_MESSAGE_FILTER, result.getCommandFilter());
    }

    @Test
    void testCapabilitiesReturnsLocalCapabilitiesIfLocalServiceInstanceIsUsed() {
        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        Optional<MemberCapabilities> resultCapabilities = testSubject.capabilities(localInstance);

        assertTrue(resultCapabilities.isPresent());
        assertEquals(LOAD_FACTOR, resultCapabilities.get().getLoadFactor());
        assertEquals(COMMAND_MESSAGE_FILTER, resultCapabilities.get().getCommandFilter());

        verifyNoInteractions(restTemplate);
    }

    @Test
    void testCapabilitiesReturnsLocalCapabilitiesIfServiceInstanceUriMatches() {
        URI testURI = URI.create("http://remote");
        when(localInstance.getUri()).thenReturn(testURI);

        ServiceInstance otherServiceWithMatchingUri = mock(ServiceInstance.class);
        when(otherServiceWithMatchingUri.getUri()).thenReturn(testURI);

        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        Optional<MemberCapabilities> resultCapabilities = testSubject.capabilities(otherServiceWithMatchingUri);

        assertTrue(resultCapabilities.isPresent());
        assertEquals(LOAD_FACTOR, resultCapabilities.get().getLoadFactor());
        assertEquals(COMMAND_MESSAGE_FILTER, resultCapabilities.get().getCommandFilter());

        verifyNoInteractions(restTemplate);
    }

    @Test
    void testCapabilitiesGetsCapabilitiesThroughRestTemplate() {
        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        URI testURI = URI.create("http://remote");
        ServiceInstance testServiceInstance = mock(ServiceInstance.class);
        when(testServiceInstance.getUri()).thenReturn(testURI);

        //noinspection unchecked
        ResponseEntity<SerializedMemberCapabilities> testResponseEntity = mock(ResponseEntity.class);
        when(testResponseEntity.getBody()).thenReturn(
                new SerializedMemberCapabilities(LOAD_FACTOR, COMMAND_MESSAGE_FILTER, serializer)
        );

        URI expectedRemoteUri = UriComponentsBuilder.fromUri(testURI)
                                                    .path(CUSTOM_ENDPOINT)
                                                    .build().toUri();
        when(restTemplate.exchange(
                eq(expectedRemoteUri), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(SerializedMemberCapabilities.class)
        )).thenReturn(testResponseEntity);

        Optional<MemberCapabilities> resultCapabilities = testSubject.capabilities(testServiceInstance);

        assertTrue(resultCapabilities.isPresent());
        assertEquals(LOAD_FACTOR, resultCapabilities.get().getLoadFactor());
        assertEquals(COMMAND_MESSAGE_FILTER, resultCapabilities.get().getCommandFilter());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(SerializedMemberCapabilities.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals("remote", resultUri.getHost());
        assertEquals(CUSTOM_ENDPOINT, resultUri.getPath());
    }

    @Test
    void testCapabilitiesRethrowsHttpClientErrorExceptionAsServiceInstanceClientException() {
        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        when(restTemplate.exchange(
                any(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(SerializedMemberCapabilities.class)
        )).thenThrow(HttpClientErrorException.class);

        URI testURI = URI.create("http://remote");
        ServiceInstance testServiceInstance = mock(ServiceInstance.class);
        when(testServiceInstance.getUri()).thenReturn(testURI);

        assertThrows(ServiceInstanceClientException.class, () -> testSubject.capabilities(testServiceInstance));

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(SerializedMemberCapabilities.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals("remote", resultUri.getHost());
        assertEquals(CUSTOM_ENDPOINT, resultUri.getPath());
    }

    @Test
    void testCapabilitiesReturnsIncapableMemberWhenNonHttpClientErrorExceptionIsThrown() {
        testSubject.updateLocalCapabilities(localInstance, LOAD_FACTOR, COMMAND_MESSAGE_FILTER);

        when(restTemplate.exchange(
                any(), eq(HttpMethod.GET), eq(HttpEntity.EMPTY), eq(SerializedMemberCapabilities.class)
        )).thenThrow(IllegalStateException.class);

        URI testURI = URI.create("http://remote");
        ServiceInstance testServiceInstance = mock(ServiceInstance.class);
        when(testServiceInstance.getUri()).thenReturn(testURI);

        Optional<MemberCapabilities> resultCapabilities = testSubject.capabilities(testServiceInstance);

        assertTrue(resultCapabilities.isPresent());

        verify(restTemplate).exchange(uriArgumentCaptor.capture(),
                                      eq(HttpMethod.GET),
                                      eq(HttpEntity.EMPTY),
                                      eq(SerializedMemberCapabilities.class));

        URI resultUri = uriArgumentCaptor.getValue();
        assertEquals("remote", resultUri.getHost());
        assertEquals(CUSTOM_ENDPOINT, resultUri.getPath());
    }
}