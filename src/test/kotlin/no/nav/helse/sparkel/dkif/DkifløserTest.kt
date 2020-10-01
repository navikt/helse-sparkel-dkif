package no.nav.helse.sparkel.dkif

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
internal class DkifløserTest {

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode
    private lateinit var service: DkifService

    private val context = object : RapidsConnection.MessageContext {
        override fun send(message: String) {
            sendtMelding = objectMapper.readTree(message)
        }

        override fun send(key: String, message: String) {}
    }

    private val rapid = object : RapidsConnection() {

        fun sendTestMessage(message: String) {
            listeners.forEach { it.onMessage(message, context) }
        }

        override fun publish(message: String) {}

        override fun publish(key: String, message: String) {}

        override fun start() {}

        override fun stop() {}
    }

    @BeforeAll
    fun setup() {
        wireMockServer.start()
        configureFor(create().port(wireMockServer.port()).build())
        stubEksterneEndepunkt()
        service = DkifService(
            DkifClient(
                baseUrl = wireMockServer.baseUrl(),
                stsClient = StsRestClient(
                    baseUrl = wireMockServer.baseUrl(),
                    serviceUser = ServiceUser("", "")
                )
            )
        )
    }

    @AfterAll
    internal fun teardown() {
        wireMockServer.stop()
    }

    @BeforeEach
    internal fun beforeEach() {
        sendtMelding = objectMapper.createObjectNode()
    }

    @Test
    fun `løser behov for digital person`() {
        testBehov(erDigital())

        assertTrue(sendtMelding.løsning())
    }

    @Test
    fun `Svarer false for person med feil`() {
        testBehov(personMedFeil())

        assertFalse(sendtMelding.løsning())
    }

    @Test
    fun `Svarer false når dkif gir error`() {
        testBehov(ikkeTilgangBehov())

        assertFalse(sendtMelding.løsning())
    }

    @Test
    fun `Svarer false når person er reservert`() {
        testBehov(erReservert())

        assertFalse(sendtMelding.løsning())
    }

    @Test
    fun `Svarer false når person ikke kan varsles`() {
        testBehov(kanIkkeVarsles())

        assertFalse(sendtMelding.løsning())
    }

    @Test
    fun `Svarer false når person er reservert og ikke kan varsles`() {
        testBehov(erReservertOgKanIkkeVarsles())

        assertFalse(sendtMelding.løsning())
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(Dkifløser.behov).path("erDigital").asBoolean()

    private fun testBehov(behov: String) {
        Dkifløser(rapid, service)
        rapid.sendTestMessage(behov)
    }

    private fun erDigital() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "fnr"
        }
        """

    private fun ikkeTilgangBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "ikkeTilgang"
        }
        """

    private fun personMedFeil() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "feil"
        }
        """

    private fun erReservert() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "reservert"
        }
        """

    private fun kanIkkeVarsles() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "kanIkkeVarsles"
        }
        """

    private fun erReservertOgKanIkkeVarsles() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "DigitalKontaktinformasjon" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "erReservertOgKanIkkeVarsles"
        }
        """

    private fun stubEksterneEndepunkt() {
        stubFor(
            get(urlPathEqualTo("/rest/v1/sts/token"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                        "token_type": "Bearer",
                        "expires_in": 3599,
                        "access_token": "1234abc"
                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("fnr"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "feil": {},
                                      "kontaktinfo": {
                                        "fnr": {
                                          "epostadresse": "string",
                                          "kanVarsles": true,
                                          "mobiltelefonnummer": "string",
                                          "personident": "string",
                                          "reservert": false,
                                          "spraak": "nb"
                                        }
                                      }
                                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("erReservert"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "feil": {},
                                      "kontaktinfo": {
                                        "erReservert": {
                                          "epostadresse": "string",
                                          "kanVarsles": true,
                                          "mobiltelefonnummer": "string",
                                          "personident": "string",
                                          "reservert": true,
                                          "spraak": "nb"
                                        }
                                      }
                                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("kanIkkeVarsles"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "feil": {},
                                      "kontaktinfo": {
                                        "kanIkkeVarsles": {
                                          "epostadresse": "string",
                                          "kanVarsles": false,
                                          "mobiltelefonnummer": "string",
                                          "personident": "string",
                                          "reservert": false,
                                          "spraak": "nb"
                                        }
                                      }
                                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("erReservertOgKanIkkeVarsles"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "feil": {},
                                      "kontaktinfo": {
                                        "erReservertOgKanIkkeVarsles": {
                                          "epostadresse": "string",
                                          "kanVarsles": false,
                                          "mobiltelefonnummer": "string",
                                          "personident": "string",
                                          "reservert": true,
                                          "spraak": "nb"
                                        }
                                      }
                                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("feilet"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """{
                                      "feil": {
                                        "feilet": {
                                            "melding": "FEIL"
                                        }
                                      },
                                      "kontaktinfo": {}
                                    }"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/personer/kontaktinformasjon"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personidenter", equalTo("ikkeTilgang"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                )
        )
    }
}
