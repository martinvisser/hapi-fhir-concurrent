package com.example

import ca.uhn.fhir.rest.client.impl.RestfulClientFactory
import org.hl7.fhir.r4.model.Bundle
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.QuestionnaireResponse
import org.hl7.fhir.r4.model.Reference
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.util.TestPropertyValues
import org.springframework.boot.web.server.LocalServerPort
import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import org.testcontainers.elasticsearch.ElasticsearchContainer
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.IOException
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = [ContainerInitializer::class])
@Testcontainers
@ActiveProfiles("test")
class DemoApplicationTests {
    @LocalServerPort
    var localServerPort: Int = -1

    @Autowired
    lateinit var restfulClientFactory: RestfulClientFactory

    @Test
    fun `search for questionnaire responses`() {
        val fhirClient = restfulClientFactory.newGenericClient("http://localhost:$localServerPort/fhir")

        val patient = fhirClient.create()
            .resource(Patient().setId("42"))
            .execute()
            .resource

        fhirClient.create()
            .resource(QuestionnaireResponse().setSubject(Reference(patient.idElement)))
            .execute()

        val numberOfThreads = 100
        val latch = CountDownLatch(numberOfThreads)
        (0 until numberOfThreads).map {
            thread {
                try {
                    fhirClient.search<Bundle>()
                        .forResource(QuestionnaireResponse::class.java).execute().entry
                } catch (ex: IOException) {
                    // ignore, it's just noice
                } finally {
                    latch.countDown()
                }
            }
        }
        latch.await()
    }
}

internal class ContainerInitializer : ApplicationContextInitializer<ConfigurableApplicationContext> {
    private val elasticsearchContainer =
        ElasticsearchContainer("docker.elastic.co/elasticsearch/elasticsearch:7.10.2")
            .withReuse(true)

    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        elasticsearchContainer.start()

        TestPropertyValues.of("spring.elasticsearch.rest.uris", "http://${elasticsearchContainer.httpHostAddress}")
    }
}
