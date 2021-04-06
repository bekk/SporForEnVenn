package Bekk.no.Models.Airtable.Update

import com.google.gson.annotations.SerializedName

data class Fields(
    @SerializedName("Spørsmål")
    val sporsmal: String,
    @SerializedName("Publisert")
    val publisert: Boolean = true,
)