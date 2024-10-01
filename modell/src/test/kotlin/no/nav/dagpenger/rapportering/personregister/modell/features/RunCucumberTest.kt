package no.nav.dagpenger.rapportering.personregister.modell.features

import io.cucumber.core.options.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@SelectClasspathResource("features") // This points to src/test/resources/features
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
class RunCucumberTest
