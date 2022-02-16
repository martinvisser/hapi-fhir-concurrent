package com.example;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.jpa.config.BaseConfig;
import ca.uhn.fhir.jpa.config.HapiFhirLocalContainerEntityManagerFactoryBean;
import ca.uhn.fhir.rest.client.apache.ApacheRestfulClientFactory;
import ca.uhn.fhir.rest.client.impl.RestfulClientFactory;
import ca.uhn.fhir.spring.boot.autoconfigure.FhirProperties;
import ca.uhn.fhir.spring.boot.autoconfigure.FhirRestfulServerCustomizer;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.PutIndexTemplateRequest;
import org.elasticsearch.common.settings.Settings;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Properties;

@SpringBootApplication(proxyBeanMethods = false)
class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}

@Configuration(proxyBeanMethods = false)
class DemoConfig {
    private final FhirProperties fhirProperties;

    DemoConfig(final FhirProperties fhirProperties) {
        this.fhirProperties = fhirProperties;
    }

    @Bean
    RestfulClientFactory restfulClientFactory(FhirContext fhirContext) {
        final var factory = new ApacheRestfulClientFactory(fhirContext);
        factory.setSocketTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setConnectTimeout((int) Duration.ofSeconds(30).toMillis());
        factory.setConnectionRequestTimeout((int) Duration.ofSeconds(30).toMillis());
        return factory;
    }

    @Bean
    FhirContext fhirContext() {
        return new FhirContext(fhirProperties.getVersion());
    }

    @Bean
    HapiFhirLocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource,
            JpaProperties jpaProperties,
            FhirContext fhirContext,
            NgramIndexConfigurator ngramIndexConfigurator,
            ConfigurableListableBeanFactory configurableListableBeanFactory
    ) {
        final var bean = new HapiFhirLocalContainerEntityManagerFactoryBean(configurableListableBeanFactory);
        bean.setDataSource(dataSource);
        var properties = new Properties();
        properties.putAll(jpaProperties.getProperties());
        bean.setJpaProperties(properties);
        BaseConfig.configureEntityManagerFactory(bean, fhirContext);
        // apply before ES indices get created
        ngramIndexConfigurator.inject();
        return bean;
    }

    @Bean
    NgramIndexConfigurator ngramIndexConfigurator(RestHighLevelClient client) {
        return new NgramIndexConfigurator(client);
    }

    @Bean
    FhirRestfulServerCustomizer serverCustomizer() {
        return server -> server.registerInterceptor(new DemoAuthorizationInterceptor());
    }
}

class NgramIndexConfigurator {
    private final RestHighLevelClient client;

    NgramIndexConfigurator(final RestHighLevelClient client) {
        this.client = client;
    }

    void inject() {
        var ngramTemplate = new PutIndexTemplateRequest("ngram-template")
                .patterns(List.of("resourcetable-*", "termconcept-*"))
                .settings(Settings.builder().put("index.max_ngram_diff", 50));
        try {
            client.indices().putTemplate(ngramTemplate, RequestOptions.DEFAULT);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
