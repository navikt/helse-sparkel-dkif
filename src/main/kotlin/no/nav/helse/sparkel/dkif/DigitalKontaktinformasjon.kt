package no.nav.helse.sparkel.dkif

import com.fasterxml.jackson.databind.JsonNode

class DigitalKontaktinformasjon(jsonNode: JsonNode) {

    private val reservert = jsonNode["reservert"].asBoolean()
    private val kanVarsles = jsonNode["kanVarsles"].asBoolean()

    val erDigital: Boolean = !reservert && kanVarsles
}
