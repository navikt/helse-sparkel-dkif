package no.nav.helse.sparkel.dkif

import no.nav.helse.rapids_rivers.RapidApplication
import no.nav.helse.rapids_rivers.RapidsConnection
import java.io.File

fun main() {
    val app = createApp(System.getenv())
    app.start()
}

internal fun createApp(env: Map<String, String>): RapidsConnection {
    val stsClient = StsRestClient(
        baseUrl = env.getValue("STS_BASE_URL"),
        serviceUser = "/var/run/secrets/nais.io/service_user".let { ServiceUser("$it/username".readFile(), "$it/password".readFile()) }
    )
    val dkifClient = DkifClient(
        baseUrl = env.getValue("DKIF_URL"),
        stsClient = stsClient
    )
    val dkifService = DkifService(dkifClient)

    return RapidApplication.create(env).apply {
        Dkifløser(this, dkifService)
    }
}

private fun String.readFile() = File(this).readText(Charsets.UTF_8)
