package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class DistinctRolesServicesActionsRepositoryTest : RepositoryTestBase({

    // Lager én test for henting av filter-verdier pga materialized view husker data fra tidligere tester
    "Should retrieve filter-values" {
        buildAndInsertTestEbmsMessageDetailFilterData(ebmsMessageDetailRepository)

        var result = distinctRolesServicesActionsRepository.getDistinctRolesServicesActions()
        result shouldBe null // null ved førstegangs kall til view pga tom test-database ved migration

        distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions()
        result = distinctRolesServicesActionsRepository.getDistinctRolesServicesActions()

        result shouldNotBe null
        result!!.roles.size shouldBe 2
        result!!.services.size shouldBe 2
        result!!.actions.size shouldBe 2
        result!!.roles shouldContain "different-role"
        result!!.services shouldContain "different-service"
        result!!.actions shouldContain "different-action"

        ebmsMessageDetailRepository.insert(buildTestEbmsMessageDetail().copy(fromRole = "another-role"))
        distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions()
        result = distinctRolesServicesActionsRepository.getDistinctRolesServicesActions()

        result shouldNotBe null
        result!!.roles.size shouldBe 3
        result!!.services.size shouldBe 2
        result!!.actions.size shouldBe 2
        result!!.roles shouldContain "another-role"

        ebmsMessageDetailRepository.insert(buildTestEbmsMessageDetail().copy(service = "another-service", action = "another-action"))
        distinctRolesServicesActionsRepository.refreshDistinctRolesServicesActions()
        result = distinctRolesServicesActionsRepository.getDistinctRolesServicesActions()

        result shouldNotBe null
        result!!.roles.size shouldBe 3
        result!!.services.size shouldBe 3
        result!!.actions.size shouldBe 3
        result!!.services shouldContain "another-service"
        result!!.actions shouldContain "another-action"

        result!!.roles shouldContainInOrder listOf("another-role", "different-role", "test-from-role")
        result!!.services shouldContainInOrder listOf("another-service", "different-service", "test-service")
        result!!.actions shouldContainInOrder listOf("another-action", "different-action", "test-action")
    }
})
