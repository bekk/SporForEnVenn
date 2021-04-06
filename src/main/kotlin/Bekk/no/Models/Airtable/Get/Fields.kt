package Bekk.no.Models.Airtable.Get

import com.google.gson.annotations.SerializedName
import java.util.*

data class Fields(
    @SerializedName("Spørsmål")
    val sporsmal: String,
    @SerializedName("Publisert")
    var publisert: Boolean = true,
    @SerializedName("Sendt inn")
    val sendtInn: Date = Date()
)
