package no.nav.dagpenger.rapportering.personregister.modell.features

import io.cucumber.core.options.Constants.GLUE_PROPERTY_NAME
import io.cucumber.core.options.Constants.PLUGIN_PROPERTY_NAME
import org.junit.platform.suite.api.ConfigurationParameter
import org.junit.platform.suite.api.SelectClasspathResource
import org.junit.platform.suite.api.Suite

@Suite
@SelectClasspathResource("features")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "no.nav.dagpenger.rapportering.personregister.modell.features.steps")
class RunCucumberTest
