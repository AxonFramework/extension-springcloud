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
 */
@ConfigurationProperties(prefix = "axon.distributed")
public class DistributedCommandBusProperties {

    /**
     * Enables Distributed Command Bus configuration for this application.
     */
    private boolean enabled = false;

    /**
     * Sets the loadFactor for this node to join with. The loadFactor sets the relative load this node will
     * receive compared to other nodes in the cluster. Defaults to 100.
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
     * Enables (if {@code true}) or disables (if {@code false}, default) the auto-configuration of a Distributed
     * Command Bus instance in the application context.
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
         * Enable a HTTP GET fallback strategy for retrieving the message routing information from other nodes in a
         * distributed Axon set up. Defaults to "true".
         */
        private boolean fallbackToHttpGet = true;

        /**
         * The URL used to perform HTTP GET requests on for retrieving another nodes message routing information in a
         * distributed Axon set up. Defaults to "/message-routing-information".
         */
        private String fallbackUrl = "/message-routing-information";

        /**
         * Indicates whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server
         * fails.
         *
         * @return whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server fails.
         */
        public boolean isFallbackToHttpGet() {
            return fallbackToHttpGet;
        }

        /**
         * Whether to fall back to HTTP GET when retrieving Instance Meta Data from the Discovery Server fails.
         *
         * @param fallbackToHttpGet whether to fall back to HTTP GET when retrieving Instance Meta Data from the
         *                          Discovery Server fails.
         */
        public void setFallbackToHttpGet(boolean fallbackToHttpGet) {
            this.fallbackToHttpGet = fallbackToHttpGet;
        }

        /**
         * Returns the URL relative to the host's root to retrieve Instance Meta Data from. This is also the address
         * where this node will expose its own Instance Meta Data.
         *
         * @return the URL relative to the host's root to retrieve Instance Meta Data from.
         */
        public String getFallbackUrl() {
            return fallbackUrl;
        }

        /**
         * Sets the URL relative to the host's root to retrieve Instance Meta Data from. This is also the address
         * where this node will expose its own Instance Meta Data.
         *
         * @param fallbackUrl the URL relative to the host's root to retrieve Instance Meta Data from.
         */
        public void setFallbackUrl(String fallbackUrl) {
            this.fallbackUrl = fallbackUrl;
        }
    }
}
