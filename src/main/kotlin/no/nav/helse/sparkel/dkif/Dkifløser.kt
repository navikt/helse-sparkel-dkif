package no.nav.helse.sparkel.dkif

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
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
            validate { it.requireKey("vedtaksperiodeId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: RapidsConnection.MessageContext) {
        sikkerlogg.error("forstod ikke $behov med melding\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: RapidsConnection.MessageContext) {
        sikkerlogg.info("mottok melding: ${packet.toJson()}")
        dkifService.løsningForBehov(
            packet["@id"].asText(),
            packet["vedtaksperiodeId"].asText(),
            packet["fødselsnummer"].asText()
        ).let { løsning ->
            packet["@løsning"] = mapOf(
                behov to (løsning?.map { Institusjonsoppholdperiode(it) } ?: emptyList())
            )
            context.send(packet.toJson().also { json ->
                sikkerlogg.info(
                    "sender svar {} for {}:\n\t{}",
                    keyValue("id", packet["@id"].asText()),
                    keyValue("vedtaksperiodeId", packet["vedtaksperiodeId"].asText()),
                    json
                )
            })
        }
    }
}
