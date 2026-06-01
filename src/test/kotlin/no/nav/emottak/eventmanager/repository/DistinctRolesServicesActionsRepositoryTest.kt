package no.nav.emottak.eventmanager.repository

import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.shouldBe

class DistinctRolesServicesActionsRepositoryTest : RepositoryTestBase({

    "Should return empty lists when no values have been added" {
        distinctRolesServicesActionsRepository.initialize()
        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles shouldBe emptyList()
        result.services shouldBe emptyList()
        result.actions shouldBe emptyList()
    }

    "Should return values after addIfAbsent" {
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "test-action")
        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles.size shouldBe 1
        result.services.size shouldBe 1
        result.actions.size shouldBe 1
        result.roles shouldContain "test-role"
        result.services shouldContain "test-service"
        result.actions shouldContain "test-action"
    }

    "Should accumulate distinct values across multiple addIfAbsent calls" {
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "test-action")
        distinctRolesServicesActionsRepository.addIfAbsent("different-role", "test-service", "test-action")
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "different-service", "test-action")
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "different-action")

        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles.size shouldBe 2
        result.services.size shouldBe 2
        result.actions.size shouldBe 2
        result.roles shouldContain "different-role"
        result.services shouldContain "different-service"
        result.actions shouldContain "different-action"
    }

    "Should not duplicate values on repeated addIfAbsent calls" {
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "test-action")
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "test-action")

        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles.size shouldBe 1
        result.services.size shouldBe 1
        result.actions.size shouldBe 1
    }

    "Should handle null role in addIfAbsent" {
        distinctRolesServicesActionsRepository.addIfAbsent(null, "test-service", "test-action")

        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles shouldBe emptyList()
        result.services shouldContain "test-service"
        result.actions shouldContain "test-action"
    }

    "Should return values sorted alphabetically ignoring case" {
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "Test-action")
        distinctRolesServicesActionsRepository.addIfAbsent("another-role", "Another-service", "another-action")
        distinctRolesServicesActionsRepository.addIfAbsent("Beta-role", "beta-service", "beta-action")

        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles.size shouldBe 3
        result.roles shouldContainInOrder listOf("another-role", "Beta-role", "test-role")
        result.services shouldContainInOrder listOf("Another-service", "beta-service", "test-service")
        result.actions shouldContainInOrder listOf("another-action", "beta-action", "Test-action")
    }

    "initialize() should reload values from DB into the in-memory cache" {
        distinctRolesServicesActionsRepository.addIfAbsent("test-role", "test-service", "test-action")
        // Calling initialize() again should re-load from DB without losing values
        distinctRolesServicesActionsRepository.initialize()
        val result = distinctRolesServicesActionsRepository.getAll()
        result.roles shouldContain "test-role"
        result.services shouldContain "test-service"
        result.actions shouldContain "test-action"
    }
})
