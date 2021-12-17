package com.example

import ca.uhn.fhir.model.primitive.IdDt
import ca.uhn.fhir.rest.api.RestOperationTypeEnum
import ca.uhn.fhir.rest.api.server.RequestDetails
import ca.uhn.fhir.rest.server.interceptor.auth.AuthorizationInterceptor
import ca.uhn.fhir.rest.server.interceptor.auth.IAuthRule
import ca.uhn.fhir.rest.server.interceptor.auth.PolicyEnum
import ca.uhn.fhir.rest.server.interceptor.auth.RuleBuilder
import org.hl7.fhir.r4.model.QuestionnaireResponse

internal class DemoAuthorizationInterceptor : AuthorizationInterceptor(PolicyEnum.ALLOW) {
    override fun buildRuleList(requestDetails: RequestDetails): List<IAuthRule> {
        if (requestDetails.restOperationType == RestOperationTypeEnum.METADATA) {
            return RuleBuilder().allowAll().build()
        }

        return demoRules()
    }

    private fun demoRules(): List<IAuthRule> {
        return RuleBuilder()
            .allow("Allow read the current patient QuestionnaireResponses").read()
            .resourcesOfType(QuestionnaireResponse::class.java).inCompartment("Patient", IdDt("Patient", "42"))
            .build()
    }
}
