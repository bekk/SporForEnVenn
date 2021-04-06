package Bekk.no.Models

import com.google.gson.annotations.Expose

data class Record(
        @Expose(serialize = false, deserialize = true)
        val id: String,
        val fields: Fields
        )
