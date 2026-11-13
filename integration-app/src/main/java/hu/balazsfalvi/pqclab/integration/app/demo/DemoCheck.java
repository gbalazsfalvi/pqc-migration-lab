package hu.balazsfalvi.pqclab.integration.app.demo;

import hu.balazsfalvi.pqclab.integration.core.MigrationPolicy;

/**
 * Records one real HTTP observation against an independently specified expectation.
 *
 * @param scenario the stable experiment identifier
 * @param policy the receiver-owned policy
 * @param expectedStatus the expected HTTP outcome
 * @param actualStatus the observed HTTP outcome
 * @param passed whether both status and any receipt-identity requirement were satisfied
 * @param observation the safe receiver reason or receipt outcome
 */
public record DemoCheck(
        String scenario,
        MigrationPolicy policy,
        int expectedStatus,
        int actualStatus,
        boolean passed,
        String observation) {}
