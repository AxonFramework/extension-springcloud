/*
 * Copyright (c) 2010-2018. Axon Framework
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

/*
 * Copyright (c) 2010-2017. Axon Framework
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

package org.axonframework.extensions.springcloud;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defines the properties for the Distributed Command Bus, when automatically configured in the Application Context.
 *
 * @author Steven van Beelen
 * @since 3.0
 */
@ConfigurationProperties(prefix = "axon.distributed")
public class DistributedCommandBusProperties {

    /**
     * Enables Distributed Command Bus configuration for this application.
     */
    private boolean enabled = false;

    /**
     * Sets the loadFactor for this node to join with. The loadFactor sets the relative load this node will receive
     * compared to other nodes in the cluster. Defaults to 100.
     */
    private int loadFactor = 100;

    private SpringCloudProperties springCloud = new SpringCloudProperties();

    /**
     * Indicates whether the (auto-configuration) of the Distributed Command Bus is enabled.
     *
     * @return whether the (auto-configuration) of the Distributed Command Bus is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Enables (if {@code true}) or disables (if {@code false}, default) the auto-configuration of a Distributed Command
     * Bus instance in the application context.
     *
     * @param enabled whether to enable Distributed Command Bus configuration.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the load factor for this instance of the Distributed Command Bus (default 100).
     *
     * @return the load factor for this instance of the Distributed Command Bus.
     */
    public int getLoadFactor() {
        return loadFactor;
    }

    /**
     * Sets the load factor for this instance of the Distributed Command Bus (default 100).
     *
     * @param loadFactor the load factor for this instance of the Distributed Command Bus.
     */
    public void setLoadFactor(int loadFactor) {
        this.loadFactor = loadFactor;
    }

    /**
     * Returns the Spring Cloud configuration to use (if Spring Cloud is on the classpath).
     *
     * @return the Spring Cloud configuration to use.
     */
    public SpringCloudProperties getSpringCloud() {
        return springCloud;
    }

    /**
     * Sets the Spring Cloud configuration to use (if Spring Cloud is on the classpath).
     *
     * @param springCloud the Spring Cloud configuration to use.
     */
    public void setSpringCloud(SpringCloudProperties springCloud) {
        this.springCloud = springCloud;
    }

    public static class SpringCloudProperties {

        /**
         * Defines the {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * used. Defaults to {@link Mode#REST}.
         */
        private Mode mode = Mode.REST;

        /**
         * Enable a HTTP GET fallback strategy for retrieving the message routing information from other nodes in a
         * distributed Axon set up. Defaults to "true".
         *
         * @deprecated in favor of using the {@link #mode} option, similarly defaulting to a REST approach
         */
        @Deprecated
        private final boolean fallbackToHttpGet = true;

        /**
         * The URL used to perform HTTP GET requests on for retrieving another nodes message routing information in a
         * distributed Axon set up. Defaults to "/message-routing-information".
         *
         * @deprecated in favor of using the {@link #restModeUrl} setting
         */
        @Deprecated
        private final String fallbackUrl = "/message-routing-information";

        /**
         * The URL used to perform HTTP GET requests on for retrieving another node's capabilities in a distributed Axon
         * set up. Defaults to {@code "/message-routing-information"}.
         */
        private String restModeUrl = "/message-routing-information";

        /**
         * The optional name of the spring cloud service instance metadata property, that does contain the context root
         * path of the service.
         */
        private String contextRootMetadataPropertyName;

        /**
         * Defines whether the created {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * is wrapped by an {@link org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingCapabilityDiscoveryMode}
         * implementations. This is enabled by default and should be disabled by setting this flag to to {@code true}.
         */
        private boolean disableIgnoreListing = false;

        /**
         * Defines the {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * used by the {@link org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter}.
         * Defaults to {@link Mode#REST}.
         *
         * @return the {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * being used
         */
        public Mode getMode() {
            return mode;
        }

        /**
         * Specify which {@link Mode} the {@link org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter}
         * should use to discover the capabilities of nodes.
         *
         * @param mode the {@link Mode} to be used by the {@link org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter}
         */
        public void setMode(Mode mode) {
            this.mode = mode;
        }

        /**
         * Indicates whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server
         * fails.
         *
         * @return whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server fails.
         * @deprecated in favor of defining a {@link #mode}
         */
        @Deprecated
        public boolean isFallbackToHttpGet() {
            return true;
        }

        /**
         * Whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server fails.
         *
         * @param fallbackToHttpGet whether to fall back to HTTP GET when retrieving Instance Meta Data from the
         *                          Discovery Server fails.
         * @deprecated in favor of defining a {@link #mode}
         */
        @Deprecated
        public void setFallbackToHttpGet(boolean fallbackToHttpGet) {
            if (fallbackToHttpGet) {
                setMode(Mode.REST);
            }
        }

        /**
         * Returns the URL relative to the host's root to retrieve Instance Meta Data from. This is also the address
         * where this node will expose its own Instance Meta Data.
         *
         * @return the URL relative to the host's root to retrieve Instance Meta Data from.
         * @deprecated in favor of {@link #getRestModeUrl()}
         */
        @Deprecated
        public String getFallbackUrl() {
            return restModeUrl;
        }

        /**
         * Sets the URL relative to the host's root to retrieve Instance Meta Data from. This is also the address where
         * this node will expose its own Instance Meta Data.
         *
         * @param fallbackUrl the URL relative to the host's root to retrieve Instance Meta Data from.
         * @deprecated in favor of {@link #setRestModeUrl(String)}
         */
        @Deprecated
        public void setFallbackUrl(String fallbackUrl) {
            this.restModeUrl = fallbackUrl;
        }

        /**
         * Returns the URL relative to the host's root to retrieve a nodes handling capabilities from. This is also the
         * address where this node will expose its own capabilities on.
         *
         * @return the URL relative to the host's root to a nodes handling capabilities from
         */
        public String getRestModeUrl() {
            return restModeUrl;
        }

        /**
         * Sets the URL relative to the host's root to retrieve a nodes handling capabilities from. This is also the
         * address where this node will expose its own capabilities on.
         *
         * @param restModeUrl the URL relative to the host's root to retrieve Instance Meta Data from.
         */
        public void setRestModeUrl(String restModeUrl) {
            this.restModeUrl = restModeUrl;
        }

        /**
         * Returns the optional name of the spring cloud service instance metadata property, which contains the context
         * root path of the service.
         *
         * @return the optional name of the spring cloud service instance metadata property, which contains the context
         * root path of the service.
         */
        public String getContextRootMetadataPropertyName() {
            return contextRootMetadataPropertyName;
        }

        /**
         * Specifies the optional name of the spring cloud service instance metadata property, which contains the
         * context root path of the service.
         *
         * @param contextRootMetadataPropertyName the optional name of the spring cloud service instance metadata
         *                                        property, which contains the context root path of the service
         */
        public void setContextRootMetadataPropertyName(String contextRootMetadataPropertyName) {
            this.contextRootMetadataPropertyName = contextRootMetadataPropertyName;
        }

        /**
         * Defines whether the created {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * (defined through the {@link #mode} settings) is wrapped in an {@link org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingCapabilityDiscoveryMode}.
         * Defaults to {@code false}, meaning ignore listing is enabled
         *
         * @return a {@code boolean} specifying whether ignore listing has been disabled
         */
        public boolean shouldDisableIgnoreListing() {
            return disableIgnoreListing;
        }

        /**
         * Sets whether the used {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         * should be wrapped in an {@link org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingCapabilityDiscoveryMode}.
         *
         * @param disableIgnoreListing a {@code boolean} defining whether the used {@link org.axonframework.extensions.springcloud.commandhandling.mode.CapabilityDiscoveryMode}
         *                             should be wrapped in an {@link org.axonframework.extensions.springcloud.commandhandling.mode.IgnoreListingCapabilityDiscoveryMode}.
         */
        public void setDisableIgnoreListing(boolean disableIgnoreListing) {
            this.disableIgnoreListing = disableIgnoreListing;
        }

        public enum Mode {

            /**
             * On "REST" mode, the {@link org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter}
             * will use a {@link org.axonframework.extensions.springcloud.commandhandling.mode.RestCapabilityDiscoveryMode}
             * to discover other nodes and to share its own capabilities.
             */
            REST,

            /**
             * On "simple" mode, the {@link org.axonframework.extensions.springcloud.commandhandling.SpringCloudCommandRouter}
             * will use a {@link org.axonframework.extensions.springcloud.commandhandling.mode.SimpleCapabilityDiscoveryMode}
             * to discover other nodes and to share its own capabilities.
             */
            SIMPLE
        }
    }
}
