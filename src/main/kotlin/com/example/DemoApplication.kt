package com.example

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.jpa.config.BaseConfig
import ca.uhn.fhir.jpa.config.HapiFhirLocalContainerEntityManagerFactoryBean
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory
import ca.uhn.fhir.spring.boot.autoconfigure.FhirProperties
import ca.uhn.fhir.spring.boot.autoconfigure.FhirRestfulServerCustomizer
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.PutIndexTemplateRequest
import org.elasticsearch.common.settings.Settings
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import java.util.Properties
import javax.sql.DataSource

@SpringBootApplication(proxyBeanMethods = false)
class DemoApplication

fun main(args: Array<String>) {
    runApplication<DemoApplication>(*args)
}

@Configuration(proxyBeanMethods = false)
class DemoConfig(val fhirProperties: FhirProperties) {
    @Bean
    fun restfulClientFactory(fhirContext: FhirContext) = ApacheRestfulClientFactory(fhirContext)
        .apply {
            socketTimeout = Duration.ofSeconds(30).toMillis().toInt()
            connectTimeout = Duration.ofSeconds(30).toMillis().toInt()
            connectionRequestTimeout = Duration.ofSeconds(30).toMillis().toInt()
        }

    @Bean
    fun fhirContext() = FhirContext(fhirProperties.version)

    @Bean
    fun entityManagerFactory(
        dataSource: DataSource,
        jpaProperties: JpaProperties,
        fhirContext: FhirContext,
        ngramIndexConfigurator: NgramIndexConfigurator,
        configurableListableBeanFactory: ConfigurableListableBeanFactory,
    ): HapiFhirLocalContainerEntityManagerFactoryBean =
        HapiFhirLocalContainerEntityManagerFactoryBean(configurableListableBeanFactory)
            .apply {
                this.dataSource = dataSource
                val properties = Properties()
                jpaProperties.properties.forEach { properties[it.key] = it.value }
                setJpaProperties(properties)
                BaseConfig.configureEntityManagerFactory(this, fhirContext)
            }
            .also {
                // apply before ES indices get created
                ngramIndexConfigurator.inject()
            }

    @Bean
    fun ngramIndexConfigurator(client: RestHighLevelClient) = NgramIndexConfigurator(client)

    @Bean
    fun serverCustomizer() = FhirRestfulServerCustomizer {
        it.registerInterceptor(DemoAuthorizationInterceptor())
    }
}

class NgramIndexConfigurator(private val client: RestHighLevelClient) {
    fun inject() {
        val ngramTemplate = PutIndexTemplateRequest("ngram-template")
            .patterns(listOf("resourcetable-*", "termconcept-*"))
            .settings(Settings.builder().put("index.max_ngram_diff", 50))
        client.indices().putTemplate(ngramTemplate, RequestOptions.DEFAULT)
    }
}
