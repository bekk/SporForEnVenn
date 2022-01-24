package Bekk.no

import Bekk.no.Models.InteractiveMessageRequest.Payload
import com.google.gson.Gson
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import com.slack.api.Slack
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.*

class InteractiveResponse {
    @FunctionName("interactiveresponse")
    fun run(
        @HttpTrigger(
            name = "req",
            methods = [HttpMethod.GET, HttpMethod.POST],
            authLevel = AuthorizationLevel.ANONYMOUS
        ) request: HttpRequestMessage<Optional<String>>,
        context: ExecutionContext
    ) {

        // BoilerPlate
        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")
        val slack = Slack.getInstance()
        val slackData = splitSlackMessage(URLDecoder.decode(request.body.get(), "Utf-8"))

        val channelId = System.getenv("SLACK_CHANNEL_ID")
        val slackToken = System.getenv("SLACK_TOKEN")
        val methods = slack.methods(slackToken)
        // BoilerPlate end

        val decodedMessage = slackData["payload"]
        val gson = Gson();
        val payload = gson.fromJson(decodedMessage, Payload::class.java)

        // ack
        slack.httpClient.postJsonBody(payload.response_url, null)


        val fromChannelId = payload.channel.id
        val testChannelId = System.getenv("SLACK_TEST_CHANNEL_ID")

        if (payload.actions[0].action_id == "publiser") {
            GlobalScope.launch {
                checkIfMessageIsFromSlack(request, payload.user.id, context.logger)
                if (testChannelId.toString() == fromChannelId)
                    publiserMessageToSlackFromAirtables(
                        payload.state.values.actions.VelgHvaSomSkalPubliseres.selected_option.value,
                        payload.response_url,
                        methods,
                        slack.httpClient,
                        testChannelId,
                    )
                else
                    publiserMessageToSlackAndUpdateAirtables(
                        payload.state.values.actions.VelgHvaSomSkalPubliseres.selected_option.value,
                        payload.response_url,
                        methods,
                        slack.httpClient,
                        channelId,
                        context.logger
                    )
            }
        }
    }
}