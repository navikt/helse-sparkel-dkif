package no.nav.helse.sparkel.dkif

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.LoggerFactory

internal class Dkifløser(
    rapidsConnection: RapidsConnection,
    private val dkifService: DkifService
) : River.PacketListener {

    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

    companion object {
        const val behov = "DigitalKontaktinformasjon"
    }

    init {
        River(rapidsConnection).apply {
            validate { it.demandAll("@behov", listOf(behov)) }
            validate { it.rejectKey("@løsning") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("fødselsnummer") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        dkifService.løsningForBehov(
            packet["@id"].asText(),
            packet["fødselsnummer"].asText()
        ).let { løsning ->
            packet["@løsning"] = mapOf(
                behov to mapOf("erDigital" to (løsning?.takeUnless { it.isMissingNode }?.let { DigitalKontaktinformasjon(it).erDigital }
                    ?: false))
            )
            context.publish(packet.toJson().also { json ->
                sikkerlogg.info(
                    "sender svar {} for {}:\n\t{}",
                    keyValue("id", packet["@id"].asText()),
                    json
                )
            })
        }
    }
}
