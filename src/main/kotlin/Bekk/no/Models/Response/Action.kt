package Bekk.no.Models.Response

data class Action(
    val action_id: String,
    val action_ts: String,
    val block_id: String,
    val selected_option: SelectedOption,
    val type: String
)