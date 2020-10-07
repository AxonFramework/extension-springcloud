/*
 * Copyright (c) 2010-2020. Axon Framework
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.axonframework.commandhandling.distributed.CommandMessageFilter;
import org.axonframework.serialization.SerializedObject;
import org.axonframework.serialization.Serializer;
import org.axonframework.serialization.SimpleSerializedObject;

import java.beans.ConstructorProperties;

/**
 * Implementation of the {@link MemberCapabilities} which stores the {@link CommandMessageFilter} in a serialized
 * format.
 *
 * @author Steven van Beelen
 * @since 4.4
 */
public class SerializedMemberCapabilities implements MemberCapabilities {

    private static final long serialVersionUID = -6885816999650990713L;

    private final int loadFactor;
    private final String serializedCommandFilter;
    private final String serializedCommandFilterType;
    private transient Serializer serializer;

    /**
     * Construct a {@link SerializedMemberCapabilities} based on the given {@code delegate}, serializing the {@link
     * CommandMessageFilter} with the given {@code serializer}
     *
     * @param delegate   the delegate {@link MemberCapabilities} to base this {@link SerializedMemberCapabilities}
     *                   instance on
     * @param serializer the {@link Serializer} to serialize the {@link MemberCapabilities#getCommandFilter()} with
     */
    protected SerializedMemberCapabilities(MemberCapabilities delegate, Serializer serializer) {
        this(delegate.getLoadFactor(), delegate.getCommandFilter(), serializer);
    }

    /**
     * Construct a {@link SerializedMemberCapabilities} based on the given {@code loadFactor} and {@code commandFilter}.
     * The {@code commandFilter} will be serialized through the {@code serializer}.
     *
     * @param loadFactor    the load factor for a given member
     * @param commandFilter the {@link CommandMessageFilter} for a given member
     * @param serializer    the {@link Serializer} to serialize the {@code commandFilter} with
     */
    protected SerializedMemberCapabilities(int loadFactor, CommandMessageFilter commandFilter, Serializer serializer) {
        this(loadFactor, serializer.serialize(commandFilter, String.class));
    }

    /**
     * Construct a {@link SerializedMemberCapabilities} out of the given {@code loadFactor} and the serialized {@link
     * CommandMessageFilter}
     *
     * @param loadFactor              the load factor for a given member
     * @param serializedCommandFilter a {@link SerializedObject} of type {@link String} containing a serialized {@link
     *                                CommandMessageFilter} for a given member
     */
    protected SerializedMemberCapabilities(int loadFactor, SerializedObject<String> serializedCommandFilter) {
        this(loadFactor, serializedCommandFilter.getData(), serializedCommandFilter.getType().getName());
    }

    /**
     * Construct a {@link SerializedMemberCapabilities} based on the serialized format of the {@link
     * CommandMessageFilter}.
     *
     * @param loadFactor                  the load factor for a given member
     * @param serializedCommandFilter     the serialized {@link CommandMessageFilter} for a given member
     * @param serializedCommandFilterType the serialized type of the {@link CommandMessageFilter} for a given member
     */
    @JsonCreator
    @ConstructorProperties({"loadFactor", "serializedCommandFilter", "serializedCommandFilterType"})
    public SerializedMemberCapabilities(@JsonProperty("loadFactor") int loadFactor,
                                        @JsonProperty("serializedCommandFilter") String serializedCommandFilter,
                                        @JsonProperty("serializedCommandFilterType") String serializedCommandFilterType) {
        this.loadFactor = loadFactor;
        this.serializedCommandFilter = serializedCommandFilter;
        this.serializedCommandFilterType = serializedCommandFilterType;
    }

    /**
     * Sets the {@link Serializer} used to deserialize the serialized {@link CommandMessageFilter}.
     *
     * @param serializer the {@link Serializer} used to deserialize the serialized {@link CommandMessageFilter}
     */
    // TODO: 21-08-20 I don't like this setter...
    public void setSerializer(Serializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public int getLoadFactor() {
        return loadFactor;
    }

    @Override
    public CommandMessageFilter getCommandFilter() {
        if (serializer == null) {
            throw new IllegalStateException("Cannot retrieve the CommandMessageFilter if no serializer has been set.");
        }
        return serializer.deserialize(new SimpleSerializedObject<>(serializedCommandFilter,
                                                                   String.class,
                                                                   serializedCommandFilterType,
                                                                   null));
    }
}
