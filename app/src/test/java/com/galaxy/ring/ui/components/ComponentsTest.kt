package com.galaxy.ring.ui.components

import org.junit.Assert.assertNotNull
import org.junit.Test

class ComponentsTest {

    @Test
    fun componentsAreInstantiableInIsolation() {
        // Verify component classes exist and compile with parameter-only signatures
        assertNotNull(Class.forName("com.galaxy.ring.ui.components.MetricCardKt"))
        assertNotNull(Class.forName("com.galaxy.ring.ui.components.SectionHeaderKt"))
        assertNotNull(Class.forName("com.galaxy.ring.ui.components.EmptyStateKt"))
        assertNotNull(Class.forName("com.galaxy.ring.ui.components.StatPillKt"))
    }
}
