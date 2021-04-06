package Bekk.no

import Bekk.no.Models.Response.Payload
import com.google.gson.Gson
import com.microsoft.azure.functions.*
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
    ): HttpResponseMessage {

        // BoilerPlate
        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")
        val slackData = request.body.get()
        val slack = Slack.getInstance()
        val slackToken = System.getenv("SLACK_TOKEN")
        val channelId = System.getenv("SLACK_CHANNEL_ID")
        val methods = slack.methods(slackToken)
        // BoilerPlate end

        val raw = URLDecoder.decode(slackData, "Utf-8")
        val decodedMessage = splitSlackMessage(raw)["payload"]
        val gson = Gson();
        val payload = gson.fromJson(decodedMessage, Payload::class.java)

        checkIfMessageIsFromSlack(request, payload.user.id, context.logger)

        if (payload.actions[0].action_id == "publiser") {
            GlobalScope.launch {
                val fromChannel = payload.channel.id
                val testChannel = System.getenv("SLACK_TEST_CHANNEL_ID")

                if (testChannel === fromChannel)
                    publiserMessageToSlack(
                        payload.state.values.actions.VelgHvaSomSkalPubliseres.selected_option.value,
                        methods,
                        testChannel,
                    )
                else
                    publiserMessageToSlackAndUpdateAirtables(
                        payload.state.values.actions.VelgHvaSomSkalPubliseres.selected_option.value,
                        payload.response_url,
                        slack,
                        channelId,
                        context.logger
                    )
            }
        }

        return request
            .createResponseBuilder(HttpStatus.ACCEPTED)
            .build()
    }
}