package com.example

import ca.uhn.fhir.context.FhirContext
import ca.uhn.fhir.jpa.config.BaseConfig
import ca.uhn.fhir.jpa.config.HapiFhirLocalContainerEntityManagerFactoryBean
import ca.uhn.fhir.model.primitive.IdDt
import ca.uhn.fhir.rest.api.RestOperationTypeEnum
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule
import ca.uhn.fhir.rest.server.interceptor.auth.PolicyEnum
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder
import ca.uhn.fhir.spring.boot.autoconfigure.FhirProperties
import ca.uhn.fhir.spring.boot.autoconfigure.FhirRestfulServerCustomizer
import org.elasticsearch.client.RequestOptions
import org.elasticsearch.client.RestHighLevelClient
import org.elasticsearch.client.indices.PutIndexTemplateRequest
import org.elasticsearch.common.settings.Settings
import org.hl7.fhir.r4.model.Patient
import org.hl7.fhir.r4.model.QuestionnaireResponse
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

internal class DemoAuthorizationInterceptor : AuthorizationInterceptor(PolicyEnum.ALLOW) {
    override fun buildRuleList(requestDetails: RequestDetails): List<IAuthRule> {
        if (requestDetails.restOperationType == RestOperationTypeEnum.METADATA) {
            return RuleBuilder().allowAll().build()
        }

        return demoRules()
    }

    private fun demoRules(): List<IAuthRule> {
        val idType = IdDt("Patient", "42")
        return RuleBuilder()
            .allow("Allow to read patient").read().resourcesOfType(Patient::class.java).withAnyId()
            .andThen()

            .allow("Allow read the current patient QuestionnaireResponses").read()
            .resourcesOfType(QuestionnaireResponse::class.java).inCompartment("Patient", idType)
            .build()
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
