package com.example;

import ca.uhn.fhir.model.primitive.IdDt;
import ca.uhn.fhir.rest.api.RestOperationTypeEnum;
import ca.uhn.fhir.rest.api.server.RequestDetails;
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor;
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule;
import ca.uhn.fhir.rest.server.interceptor.auth.PolicyEnum;
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder;
import org.hl7.fhir.r4.model.QuestionnaireResponse;

import java.util.List;

class DemoAuthorizationInterceptor extends AuthorizationInterceptor {
    DemoAuthorizationInterceptor() {
        super(PolicyEnum.ALLOW);
    }

    public List<IAuthRule> buildRuleList(RequestDetails requestDetails) {
        if (requestDetails.getRestOperationType() == RestOperationTypeEnum.METADATA) {
            return new RuleBuilder().allowAll().build();
        }

        return demoRules();
    }

    private List<IAuthRule> demoRules() {
        return new RuleBuilder()
                .allow("Allow read the current patient QuestionnaireResponses").read()
                .resourcesOfType(QuestionnaireResponse.class).inCompartment("Patient", new IdDt("Patient", "42"))
                .build();
    }
}
