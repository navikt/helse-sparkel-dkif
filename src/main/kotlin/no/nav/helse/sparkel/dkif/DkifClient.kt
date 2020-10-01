package no.nav.helse.sparkel.dkif

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal class DkifClient(
    private val baseUrl: String,
    private val stsClient: StsRestClient
) {

    companion object {
        private val objectMapper = ObjectMapper()
    }

    internal fun hentDigitalKontaktinformasjon(
        fødselsnummer: String,
        behovId: String
    ): JsonNode {
        val url = "${baseUrl}/api/v1/personer/kontaktinformasjon"

        val (responseCode, responseBody) = with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 10000
            setRequestProperty("Authorization", "Bearer ${stsClient.token()}")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Nav-Call-Id", behovId)
            setRequestProperty("Nav-Consumer-Id", "srvsparkeldkif")
            setRequestProperty("Nav-Personidenter", fødselsnummer)

            val stream: InputStream? = if (responseCode < 300) this.inputStream else this.errorStream
            responseCode to stream?.bufferedReader()?.readText()
        }

        if (responseCode >= 300 || responseBody == null) {
            throw RuntimeException("unknown error (responseCode=$responseCode) from dkif")
        }

        return objectMapper.readTree(responseBody)
    }
}
