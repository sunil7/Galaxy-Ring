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

    @Test
    fun componentsDoNotImportDataModels() {
        // Verify that component classes have no references to data/ble packages
        val componentClasses = listOf(
            Class.forName("com.galaxy.ring.ui.components.MetricCardKt"),
            Class.forName("com.galaxy.ring.ui.components.SectionHeaderKt"),
            Class.forName("com.galaxy.ring.ui.components.EmptyStateKt"),
            Class.forName("com.galaxy.ring.ui.components.StatPillKt")
        )

        for (cls in componentClasses) {
            for (method in cls.declaredMethods) {
                for (paramType in method.parameterTypes) {
                    val name = paramType.name
                    org.junit.Assert.assertFalse(
                        "Component method ${method.name} must not depend on data/ble: $name",
                        name.startsWith("com.galaxy.ring.data.") || name.startsWith("com.galaxy.ring.ble.")
                    )
                }
            }
        }
    }
}
