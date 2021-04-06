package Bekk.no.Models.InteractiveMessageRequest

data class Container(
    val channel_id: String,
    val is_ephemeral: Boolean,
    val message_ts: String,
    val type: String
)