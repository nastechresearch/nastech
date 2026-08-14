package io.github.nastechresearch.nastech.ui.pages.setting.components

import me.rerere.ai.provider.ProviderSetting
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRequirementsTest {
    @Test
    fun `cloud providers have no device-specific requirements`() {
        assertTrue(ProviderRequirement.from(ProviderSetting.OpenAI()).isEmpty())
        assertTrue(ProviderRequirement.from(ProviderSetting.Google()).isEmpty())
        assertTrue(ProviderRequirement.from(ProviderSetting.Claude()).isEmpty())
    }
}
