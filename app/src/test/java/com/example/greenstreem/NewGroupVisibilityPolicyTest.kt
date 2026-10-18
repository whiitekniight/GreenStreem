package com.example.greenstreem

import org.junit.Assert.assertEquals
import org.junit.Test

class NewGroupVisibilityPolicyTest {
    @Test fun firstLoadPreservesExistingGroupsEvenWhenAutoShowIsOff() {
        assertEquals(emptySet<String>(), NewGroupVisibilityPolicy.newlyHidden(setOf("news", "sports"), null, false))
    }

    @Test fun providerAdditionsAreHiddenWhenAutoShowIsOff() {
        assertEquals(setOf("new-sports", "new-news"), NewGroupVisibilityPolicy.newlyHidden(
            setOf("news", "sports", "new-sports", "new-news"), setOf("news", "sports"), false))
    }

    @Test fun defaultOnAllowsNewGroups() {
        assertEquals(emptySet<String>(), NewGroupVisibilityPolicy.newlyHidden(setOf("old", "new"), setOf("old"), true))
    }

    @Test fun manuallyEnabledNewGroupStaysEnabledOnLaterRefresh() {
        val knownAfterDiscovery = setOf("old", "new")
        assertEquals(emptySet<String>(), NewGroupVisibilityPolicy.newlyHidden(knownAfterDiscovery, knownAfterDiscovery, false))
    }

    @Test fun returningGroupIsNotTreatedAsNewAndRenamingDoesNotMatter() {
        assertEquals(setOf("brand-new"), NewGroupVisibilityPolicy.newlyHidden(
            setOf("returned-id", "renamed-id", "brand-new"), setOf("returned-id", "renamed-id", "absent-id"), false))
    }

    @Test fun emptyListDoesNotHideAnything() {
        assertEquals(emptySet<String>(), NewGroupVisibilityPolicy.newlyHidden(emptySet(), setOf("old"), false))
    }
}
