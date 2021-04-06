package Bekk.no.Models.InteractiveMessageRequest

data class Payload(
    val actions: List<Action>,
    val api_app_id: String,
    val channel: Channel,
    val container: Container,
    val enterprise: Any,
    val is_enterprise_install: Boolean,
    val response_url: String,
    val state: State,
    val team: Team,
    val token: String,
    val trigger_id: String,
    val type: String,
    val user: User
)