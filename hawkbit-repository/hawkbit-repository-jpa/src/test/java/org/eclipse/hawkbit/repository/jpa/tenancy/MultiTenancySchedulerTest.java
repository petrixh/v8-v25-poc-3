/**
 * Copyright (c) 2023 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.repository.jpa.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.ACTION_CLEANUP_ACTION_EXPIRY;
import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.ACTION_CLEANUP_ACTION_STATUS;
import static org.eclipse.hawkbit.tenancy.configuration.TenantConfigurationProperties.TenantConfigurationKey.ACTION_CLEANUP_ENABLED;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import org.eclipse.hawkbit.repository.autoassign.AutoAssignExecutor;
import org.eclipse.hawkbit.repository.jpa.AbstractJpaIntegrationTest;
import org.eclipse.hawkbit.repository.jpa.autoassign.AutoAssignScheduler;
import org.eclipse.hawkbit.repository.jpa.autocleanup.AutoActionCleanup;
import org.eclipse.hawkbit.repository.jpa.autocleanup.AutoCleanupScheduler;
import org.eclipse.hawkbit.repository.model.Action.Status;
import org.eclipse.hawkbit.repository.model.DistributionSet;
import org.eclipse.hawkbit.repository.model.Target;
import org.eclipse.hawkbit.repository.test.util.DisposableSqlTestDatabaseExtension;
import org.eclipse.hawkbit.repository.test.util.SecurityContextSwitch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.support.locks.LockRegistry;

import io.qameta.allure.Description;
import io.qameta.allure.Feature;
import io.qameta.allure.Story;

/**
 * The scheduled jobs (auto assignment, action cleanup) run once for all
 * tenants, from a scheduler thread that has no user. Each tenant's part must
 * run in that tenant only: its own targets, its own distribution sets, its own
 * configuration, though both tenants use the same controller IDs.
 */
@Feature("Component Tests - Repository")
@Story("Multi Tenancy")
@ExtendWith(DisposableSqlTestDatabaseExtension.class)
class MultiTenancySchedulerTest extends AbstractJpaIntegrationTest {

    // each test has its own tenants, so the tests do not depend on each other's data
    private static final String ASSIGN_TENANT_A = "assignTenantA";
    private static final String ASSIGN_TENANT_B = "assignTenantB";
    private static final String CLEANUP_TENANT_A = "cleanupTenantA";
    private static final String CLEANUP_TENANT_B = "cleanupTenantB";
    private static final List<String> CONTROLLER_IDS = List.of("dev-1", "dev-2", "dev-3");

    @Autowired
    private AutoAssignExecutor autoAssignExecutor;

    @Autowired
    private AutoActionCleanup autoActionCleanup;

    @Autowired
    private LockRegistry lockRegistry;

    @Test
    @Description("The auto assignment scheduler gives each tenant's targets that tenant's auto-assign distribution set, never the other tenant's, though the controller IDs and filters are the same")
    void autoAssignSchedulerKeepsTenantsApart() throws Exception {
        final Long dsA = autoAssignmentWithTargets(ASSIGN_TENANT_A, "dsA");
        final Long dsB = autoAssignmentWithTargets(ASSIGN_TENANT_B, "dsB");

        new AutoAssignScheduler(systemManagement, systemSecurityContext, autoAssignExecutor, lockRegistry)
                .autoAssignScheduler();

        assertThat(asTenant(ASSIGN_TENANT_A, this::assignedDistributionSets))
                .as("distribution set of each target of tenant A").containsExactly(dsA, dsA, dsA);
        assertThat(asTenant(ASSIGN_TENANT_B, this::assignedDistributionSets))
                .as("distribution set of each target of tenant B").containsExactly(dsB, dsB, dsB);
        assertThat(asTenant(ASSIGN_TENANT_A, this::actionCount)).as("actions in tenant A").isEqualTo(3);
        assertThat(asTenant(ASSIGN_TENANT_B, this::actionCount)).as("actions in tenant B").isEqualTo(3);
    }

    @Test
    @Description("The cleanup scheduler applies each tenant's own cleanup configuration: tenant A (cleanup on) loses its canceled action, tenant B (cleanup off) keeps its own")
    void autoCleanupSchedulerAppliesEachTenantsOwnConfiguration() throws Exception {
        final Long actionA = asTenant(CLEANUP_TENANT_A, () -> canceledActionWithCleanup(true));
        final Long actionB = asTenant(CLEANUP_TENANT_B, () -> canceledActionWithCleanup(false));

        new AutoCleanupScheduler(systemManagement, systemSecurityContext, lockRegistry, List.of(autoActionCleanup))
                .run();

        assertThat(asTenant(CLEANUP_TENANT_A, () -> actionRepository.findById(actionA)))
                .as("canceled action of tenant A").isEmpty();
        assertThat(asTenant(CLEANUP_TENANT_B, () -> actionRepository.findById(actionB)))
                .as("canceled action of tenant B").isPresent();
    }

    private Long autoAssignmentWithTargets(final String tenant, final String dsName) throws Exception {
        final Long dsId = asTenant(tenant, () -> {
            CONTROLLER_IDS.forEach(testdataFactory::createTarget);
            return testdataFactory.createDistributionSet(dsName).getId();
        });
        // the filter stores its creator's context (Java serialization in the tests): create it as system code
        // in the tenant, the test helper's user is not serializable
        tenantAware.runAsTenant(tenant, () -> targetFilterQueryManagement.updateAutoAssignDS(entityFactory
                .targetFilterQuery()
                .updateAutoAssign(targetFilterQueryManagement
                        .create(entityFactory.targetFilterQuery().create().name("devices").query("controllerId==dev-*"))
                        .getId())
                .ds(dsId)));
        return dsId;
    }

    private List<Long> assignedDistributionSets() {
        return CONTROLLER_IDS.stream().map(deploymentManagement::getAssignedDistributionSet)
                .map(ds -> ds.map(DistributionSet::getId).orElse(null)).collect(Collectors.toList());
    }

    private long actionCount() {
        return actionRepository.count();
    }

    private Long canceledActionWithCleanup(final boolean cleanupEnabled) {
        tenantConfigurationManagement.addOrUpdateConfiguration(ACTION_CLEANUP_ENABLED, cleanupEnabled);
        tenantConfigurationManagement.addOrUpdateConfiguration(ACTION_CLEANUP_ACTION_EXPIRY, 0L);
        tenantConfigurationManagement.addOrUpdateConfiguration(ACTION_CLEANUP_ACTION_STATUS,
                Status.CANCELED.toString());

        final Target target = testdataFactory.createTarget(CONTROLLER_IDS.get(0));
        final DistributionSet ds = testdataFactory.createDistributionSet("ds");
        final Long actionId = getFirstAssignedActionId(assignDistributionSet(ds.getId(), target.getControllerId()));
        deploymentManagement.cancelAction(actionId);
        deploymentManagement.forceQuitAction(actionId);
        assertThat(actionRepository.findById(actionId)).as("canceled action before the cleanup").isPresent();
        return actionId;
    }

    private static <T> T asTenant(final String tenant, final Callable<T> callable) throws Exception {
        return SecurityContextSwitch.runAs(SecurityContextSwitch.withUserAndTenant("user", tenant), callable);
    }
}
