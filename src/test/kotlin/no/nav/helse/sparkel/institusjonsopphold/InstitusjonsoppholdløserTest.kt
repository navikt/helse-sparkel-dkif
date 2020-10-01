package no.nav.helse.sparkel.institusjonsopphold

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import no.nav.helse.rapids_rivers.RapidsConnection
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.TestInstance.Lifecycle

@TestInstance(Lifecycle.PER_CLASS)
internal class InstitusjonsoppholdløserTest {

    private val wireMockServer: WireMockServer = WireMockServer(WireMockConfiguration.options().dynamicPort())
    private val objectMapper = jacksonObjectMapper()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .registerModule(JavaTimeModule())

    private lateinit var sendtMelding: JsonNode
    private lateinit var service: InstitusjonsoppholdService

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
        service = InstitusjonsoppholdService(
            InstitusjonsoppholdClient(
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
    fun `løser behov`() {
        testBehov(enkeltBehov())

        val perioder = sendtMelding.løsning()

        assertEquals(2, perioder.size)
    }

    @Test
    fun `returnerer tom liste hvis ikke tilgang til Infotrygd`() {
        testBehov(ikkeTilgangBehov())

        val perioder = sendtMelding.løsning()

        assertTrue(perioder.isEmpty())
    }

    private fun JsonNode.løsning() = this.path("@løsning").path(Institusjonsoppholdløser.behov).map {
        Institusjonsoppholdperiode(it)
    }

    private fun testBehov(behov: String) {
        Institusjonsoppholdløser(rapid, service)
        rapid.sendTestMessage(behov)
    }

    private fun enkeltBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "Institusjonsopphold" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "fnr",
            "institusjonsoppholdFom": "2020-01-01",
            "institusjonsoppholdTom": "2020-01-31"
        }
        """

    private fun ikkeTilgangBehov() =
        """
        {
            "@event_name" : "behov",
            "@behov" : [ "Institusjonsopphold" ],
            "@id" : "id",
            "@opprettet" : "2020-05-18",
            "spleisBehovId" : "spleisBehovId",
            "vedtaksperiodeId" : "vedtaksperiodeId",
            "fødselsnummer" : "ikkeTilgang",
            "institusjonsoppholdFom": "2020-01-01",
            "institusjonsoppholdTom": "2020-01-31"
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
            get(urlPathEqualTo("/api/v1/person/institusjonsopphold"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personident", equalTo("fnr"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            """[
                                          {
                                            "oppholdId": 0,
                                            "tssEksternId": "string",
                                            "organisasjonsnummer": "string",
                                            "institusjonstype": "FO",
                                            "varighet": "string",
                                            "kategori": "S",
                                            "startdato": "2020-01-01",
                                            "faktiskSluttdato": "2020-01-31",
                                            "forventetSluttdato": "2020-01-31",
                                            "kilde": "string",
                                            "overfoert": true,
                                            "endretAv": "string",
                                            "endringstidspunkt": "2020-09-30T10:47:17.319Z"
                                          },
                                          {
                                            "oppholdId": 0,
                                            "tssEksternId": "string",
                                            "organisasjonsnummer": "string",
                                            "institusjonstype": "FO",
                                            "varighet": "string",
                                            "kategori": "S",
                                            "startdato": "2019-01-01",
                                            "faktiskSluttdato": "2019-01-31",
                                            "forventetSluttdato": "2019-01-31",
                                            "kilde": "string",
                                            "overfoert": true,
                                            "endretAv": "string",
                                            "endringstidspunkt": "2020-09-30T10:47:17.319Z"
                                          },
                                          {
                                            "oppholdId": 0,
                                            "tssEksternId": "string",
                                            "organisasjonsnummer": "string",
                                            "institusjonstype": "FO",
                                            "varighet": "string",
                                            "kategori": "S",
                                            "startdato": "2019-01-01",
                                            "faktiskSluttdato": null,
                                            "forventetSluttdato": "2019-01-31",
                                            "kilde": "string",
                                            "overfoert": true,
                                            "endretAv": "string",
                                            "endringstidspunkt": "2020-09-30T10:47:17.319Z"
                                          },
                                          {
                                            "oppholdId": 0,
                                            "tssEksternId": "string",
                                            "organisasjonsnummer": "string",
                                            "institusjonstype": "FO",
                                            "varighet": "string",
                                            "kategori": "S",
                                            "startdato": "2020-02-01",
                                            "faktiskSluttdato": null,
                                            "forventetSluttdato": "2020-03-31",
                                            "kilde": "string",
                                            "overfoert": true,
                                            "endretAv": "string",
                                            "endringstidspunkt": "2020-09-30T10:47:17.319Z"
                                          }
                                    ]"""
                        )
                )
        )
        stubFor(
            get(urlPathEqualTo("/api/v1/person/institusjonsopphold"))
                .withHeader("Accept", equalTo("application/json"))
                .withHeader("Nav-Personident", equalTo("ikkeTilgang"))
                .withHeader("Nav-Call-Id", equalTo("id"))
                .willReturn(
                    aResponse()
                        .withStatus(401)
                )
        )
    }
}
