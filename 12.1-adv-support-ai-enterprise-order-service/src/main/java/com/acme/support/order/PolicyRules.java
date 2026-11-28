package com.acme.support.order;

import org.kie.api.KieBase;
import org.kie.api.KieServices;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.utils.KieHelper;
import org.springframework.stereotype.Service;

@Service
public class PolicyRules {

    private final KieBase kieBase;

    public PolicyRules() {

        KieHelper helper = new KieHelper();

        helper.addResource(
                KieServices.Factory.get()
                        .getResources()
                        .newClassPathResource("rules/ticket-rules.drl"),
                ResourceType.DRL
        );

        this.kieBase = helper.build();
    }

    public String evaluate(double amount) {

        OrderPolicyFact fact = new OrderPolicyFact(amount);

        KieSession session = kieBase.newKieSession();

        try {
            session.insert(fact);
            session.fireAllRules();

            return fact.getDecision() != null
                    ? fact.getDecision()
                    : "MANUAL_REVIEW";

        } finally {
            session.dispose();
        }
    }
}