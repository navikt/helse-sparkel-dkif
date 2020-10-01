package no.nav.helse.sparkel.dkif

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import org.slf4j.MDC

internal class DkifService(private val dkifClient: DkifClient) {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
    private val log = LoggerFactory.getLogger(this::class.java)

    fun løsningForBehov(
        behovId: String,
        vedtaksperiodeId: String,
        fødselsnummer: String
    ): JsonNode? = withMDC("id" to behovId, "vedtaksperiodeId" to vedtaksperiodeId) {
        try {
            val kontaktinformasjon = dkifClient.hentDigitalKontaktinformasjon(
                fødselsnummer = fødselsnummer,
                behovId = behovId
            ).path("kontaktinfo").path(fødselsnummer)
            log.info(
                "løser behov: {} for {}",
                keyValue("id", behovId),
                keyValue("vedtaksperiodeId", vedtaksperiodeId)
            )
            sikkerlogg.info(
                "løser behov: {} for {}",
                keyValue("id", behovId),
                keyValue("vedtaksperiodeId", vedtaksperiodeId)
            )
            kontaktinformasjon
        } catch (err: Exception) {
            log.warn(
                "feil ved henting av dkif-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                err
            )
            sikkerlogg.warn(
                "feil ved henting av dkif-data: ${err.message} for {}",
                keyValue("vedtaksperiodeId", vedtaksperiodeId),
                err
            )
            null
        }
    }
}

private fun <T> withMDC(vararg values: Pair<String, String>, block: () -> T): T = try {
    values.forEach { (key, value) -> MDC.put(key, value) }
    block()
} finally {
    values.forEach { (key, _) -> MDC.remove(key) }
}
