package com.example;

import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.QuestionnaireResponse;
import org.hl7.fhir.r4.model.Reference;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.fail;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = ContainerInitializer.class)
@Testcontainers
@ActiveProfiles("test")
class DemoApplicationTests {
    @LocalServerPort
    private int localServerPort;

    @Autowired
    private RestfulClientFactory restfulClientFactory;

    @Test
    void searchForQuestionnaireResponses() throws InterruptedException {
        var fhirClient = restfulClientFactory.newGenericClient("http://localhost:" + localServerPort + "/fhir");

        var patient = fhirClient.create()
                .resource(new Patient().setId("42"))
                .execute()
                .getResource();

        fhirClient.create()
                .resource(new QuestionnaireResponse().setSubject(new Reference(patient.getIdElement())))
                .execute();

        var numberOfThreads = 10;
        var latch = new CountDownLatch(numberOfThreads);

        var exceptions = new ArrayList<Throwable>();
        IntStream.range(0, numberOfThreads).forEach(i ->
                new Thread(() -> {
                    try {
                        fhirClient.search()
                                .forResource(QuestionnaireResponse.class).execute();
                    } catch (final Exception e) {
                        exceptions.add(e);
                    }
                    latch.countDown();
                }).start()
        );

        latch.await(10, TimeUnit.SECONDS);

        if (!exceptions.isEmpty()) {
            fail("Caught one or more exceptions during multithreaded test, see console", exceptions.get(0));
        }
    }
}

class ContainerInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    private final ElasticsearchContainer elasticsearchContainer =
            new ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.10.2")
                    .withReuse(true);

    @Override
    public void initialize(final @NotNull ConfigurableApplicationContext applicationContext) {
        elasticsearchContainer.start();
        TestPropertyValues.of(
                "spring.jpa.properties.hibernate.search.backend.hosts=" + elasticsearchContainer.getHttpHostAddress(),
                "spring.elasticsearch.rest.uris=http://" + elasticsearchContainer.getHttpHostAddress()
        ).applyTo(applicationContext);
    }
}
