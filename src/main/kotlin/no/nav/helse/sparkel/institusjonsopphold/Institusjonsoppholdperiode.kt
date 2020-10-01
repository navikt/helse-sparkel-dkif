package no.nav.helse.sparkel.institusjonsopphold

import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

class Institusjonsoppholdperiode(jsonNode: JsonNode) {
    val institusjonstype = jsonNode["institusjonstype"].asEnumValue<Institusjonstype>()
    val kategori = jsonNode["kategori"].asEnumValue<Oppholdstype>()
    val startdato = jsonNode["startdato"].textValue().let { LocalDate.parse(it) }
    val faktiskSluttdato = jsonNode["faktiskSluttdato"]?.takeUnless { it.isNull }?.textValue()?.let { LocalDate.parse(it) }

    internal companion object {
        internal fun List<Institusjonsoppholdperiode>.filtrer(fom: LocalDate, tom: LocalDate) = filter { it.overlapperMed(fom, tom) }
    }

    internal fun overlapperMed(fom: LocalDate, tom: LocalDate) =
        maxOf(startdato, fom) <= faktiskSluttdato?.let { minOf(it, tom) } ?: tom

}

enum class Institusjonstype(val beskrivelse: String) {
    AS("Alders- og sykehjem"),
    FO("Fengsel"),
    HS("Helseinstitusjon")
}

enum class Oppholdstype(val beskrivelse: String) {
    A("Alders- og sykehjem"),
    D("Dagpasient"),
    F("Ferieopphold"),
    H("Heldøgnpasient"),
    P("Fødsel"),
    R("Opptreningsinstitusjon"),
    S("Soningsfange"),
    V("Varetektsfange")
}

private inline fun <reified T : Enum<T>> JsonNode?.asEnumValue() = this?.takeUnless { it.isNull }?.textValue()?.takeUnless { it == "" }?.let { enumValueOf<T>(it) }
