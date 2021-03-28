package Bekk.no

import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import com.slack.api.Slack
import kotlinx.coroutines.runBlocking
import java.util.*

class Sporforenvenn {

    @FunctionName("sporforenvenn")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.GET, HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext) {

        // BoilerPlate
        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")
        val slackData = request.body
        val slackTimestamp: String = request.headers["x-slack-request-timestamp"] ?: throw RuntimeException("Cannot get slackTimeStamp from request")
        val slackSignature: String = request.headers["x-slack-signature"] ?: throw RuntimeException("Cannot get slack signature from request")
        val slackSigningBaseString = "v0:$slackTimestamp:$slackData"
        matchSignature(slackTimestamp, slackSigningBaseString, slackSignature, context.logger)

        val channelId = "C01SGD5G1FU"
        val slack = Slack.getInstance()
        val slackToken = System.getenv("SLACK_TOKEN")

        val methods = slack.methods(slackToken)
        // BoilerPlate end

        sendOkToSlack(methods, channelId, context.logger)

        runBlocking {
            askWichMessageToPublish(methods, channelId, context.logger)
        }
    }
}
