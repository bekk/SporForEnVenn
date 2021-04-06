package Bekk.no

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

class Sporforenvenn {
    @FunctionName("sporforenvenn")
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
        val slackData = splitSlackMessage(URLDecoder.decode(request.body.get(), "Utf-8"))
        val slack = Slack.getInstance()

        // ack
        slack.httpClient.postJsonBody(slackData["response_url"], null)

        val slackToken = System.getenv("SLACK_TOKEN")
        val methods = slack.methods(slackToken)
        val user = slackData["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")
        checkIfMessageIsFromSlack(request, user, context.logger)
        // BoilerPlate end

        // val user = splitSlackMessage(slackData)["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")
        // val responseUrl = splitSlackMessage(slackData)["response_url"]?: throw RuntimeException("Cannot get user from the slash comand")
        val fromChannelId =
            slackData["channel_id"] ?: throw RuntimeException("Cannot get channel id from the slash comand")
        val testChannelId = System.getenv("SLACK_TEST_CHANNEL_ID")

        GlobalScope.launch {
            val text = slackData["text"]
            if (text != null && text != "") {
                if (fromChannelId === testChannelId) {
                    publiserMessageToSlack(text, methods, fromChannelId)
                }
                val channelId = System.getenv("SLACK_CHANNEL_ID")
                publiserMessageToSlackAndCreate(text, methods, channelId, context.logger)
            } else {
                askWhichMessageToPublish(
                    user,
                    methods,
                    // Direktemeldinger sendes fra appen til brukeren i app chatten da det ikke er lov å sende
                    // Ephemeral meldinger i direkte meldinger
                    if (!fromChannelId.startsWith("C"))
                        user
                    else
                    // Starts with C is channel så dette sendes til channel
                        fromChannelId
                )
            }
        }
    }
}
