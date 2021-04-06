package Bekk.no

import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import com.slack.api.Slack
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
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
    ): HttpResponseMessage {

        // BoilerPlate
        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")
        val slackData = request.body.get()

        val slack = Slack.getInstance()
        val slackToken = System.getenv("SLACK_TOKEN")
        val methods = slack.methods(slackToken)
        val user = splitSlackMessage(slackData)["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")
        checkIfMessageIsFromSlack(request, user, context.logger)
        // BoilerPlate end

        // val user = splitSlackMessage(slackData)["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")
        // val responseUrl = splitSlackMessage(slackData)["response_url"]?: throw RuntimeException("Cannot get user from the slash comand")

        GlobalScope.launch {
            val text = splitSlackMessage(slackData)["text"]

            if (text != null && text != ""){
                val channelId = System.getenv("SLACK_CHANNEL_ID")
                publiserMessageToSlack(text, methods, channelId, context.logger)
            }else{
                val channelId = splitSlackMessage(slackData)["channel_id"] ?: throw RuntimeException("Cannot get channel id from the slash comand")
                askWhichMessageToPublish(slackData, methods, channelId, context.logger)
            }
        }

        return request
            .createResponseBuilder(HttpStatus.ACCEPTED)
            .build()
    }
}
