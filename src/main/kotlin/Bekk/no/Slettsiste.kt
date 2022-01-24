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

class Slettsiste {
    @FunctionName("${PREFIX}slettsiste")
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
        val auth = slack.methods().authTest{
            it
                .token(slackToken)
        }

        val user = slackData["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")

        GlobalScope.launch {
            checkIfMessageIsFromSlack(request, user, context.logger)
            // BoilerPlate end

            val fromChannelId =
                    slackData["channel_id"] ?: throw RuntimeException("Cannot get channel id from the slash comand")

            val response = methods.conversationsHistory {
                it
                        .channel(fromChannelId)
            }
            val messageToBeDeleted = response.messages.first { it.botId == auth.botId };
            methods.chatDelete {
                it
                        .channel(fromChannelId)
                        .ts(messageToBeDeleted.ts)
            }
        }
    }
}