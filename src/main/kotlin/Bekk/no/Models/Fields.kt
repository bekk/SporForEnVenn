package Bekk.no.Models

import com.google.gson.annotations.SerializedName
import java.util.*

data class Fields(
    @SerializedName("Spørsmål")
    val spørsmål: String,
    @SerializedName("Publisert")
    var publisert: Boolean = false,
    @SerializedName("Sendt inn")
    var sendtInn: Date = Date(),
)
