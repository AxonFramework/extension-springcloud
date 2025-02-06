# Axon Framework - Spring Cloud Extension
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/org.axonframework.extensions.springcloud/axon-springcloud/badge.svg)](https://maven-badges.herokuapp.com/maven-central/org.axonframework.extensions.springcloud/axon-springcloud/)
![Build Status](https://github.com/AxonFramework/extension-springcloud/workflows/Spring%20Cloud%20Extension/badge.svg?branch=master)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=AxonFramework_extension-springcloud&metric=alert_status)](https://sonarcloud.io/dashboard?id=AxonFramework_extension-springcloud)

Axon Framework is a framework for building evolutionary, event-driven microservice systems,
 based on the principles of Domain Driven Design, Command-Query Responsibility Segregation (CQRS) and Event Sourcing.

As such it provides you the necessary building blocks to follow these principles. 
Building blocks like Aggregate factories and Repositories, Command, Event and Query Buses and an Event Store.
The framework provides sensible defaults for all of these components out of the box.

This set up helps you create a well structured application without having to bother with the infrastructure.
The main focus can thus become your business functionality.

This repository provides an extension to the Axon Framework: Spring Cloud.
It provides functionality to distribute command message between Axon application through means of a Spring Cloud
 implementation of the `CommandRouter` and `CommandBusConnector`.
As it follow Spring Cloud's standard, several implementation of Spring Cloud (e.g. Eureka, Consul) can be used to
 fulfill the routing job.
This extension should be regarded as a partial replacement of [Axon Server](https://axoniq.io/product-overview/axon-server),
 since it only cover the command routing part.
  
For more information on anything Axon, please visit our website, [http://axoniq.io](http://axoniq.io).

## Getting started

The [AxonIQ Docs](https://docs.axoniq.io/home/) contains a section for the guides of all the Axon Framework extensions.
The Spring Cloud extension guide can be found [here](https://docs.axoniq.io/spring-cloud-extension-reference/latest/).

## Receiving help

Are you having trouble using the extension? 
We'd like to help you out the best we can!
There are a couple of things to consider when you're traversing anything Axon:

* Checking the [documentation](https://docs.axoniq.io/home/) should be your first stop,
  as the majority of possible scenarios you might encounter when using Axon should be covered there.
* If the Reference Guide does not cover a specific topic you would've expected,
  we'd appreciate if you could post a [new thread/topic on our library fourms describing the problem](https://discuss.axoniq.io/c/26).
* There is a [forum](https://discuss.axoniq.io/) to support you in the case the reference guide did not sufficiently answer your question.
Axon Framework and Server developers will help out on a best effort basis.
Know that any support from contributors on posted question is very much appreciated on the forum.
* Next to the forum we also monitor Stack Overflow for any questions which are tagged with `axon`.

## Feature requests and issue reporting

We use GitHub's [issue tracking system](https://github.com/AxonFramework/extension-springcloud/issues) for new feature 
request, extension enhancements and bugs. 
Prior to filing an issue, please verify that it's not already reported by someone else.

When filing bugs:
* A description of your setup and what's happening helps us figuring out what the issue might be
* Do not forget to provide version you're using
* If possible, share a stack trace, using the Markdown semantic ```

When filing features:
* A description of the envisioned addition or enhancement should be provided
* (Pseudo-)Code snippets showing what it might look like help us understand your suggestion better 
* If you have any thoughts on where to plug this into the framework, that would be very helpful too
* Lastly, we value contributions to the framework highly. So please provide a Pull Request as well!
 