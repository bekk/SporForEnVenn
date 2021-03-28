package Bekk.no

import Bekk.no.Models.Records
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.*
import com.slack.api.Slack
import com.slack.api.methods.SlackApiException
import kotlinx.coroutines.runBlocking
import java.io.IOException
import java.util.*
import com.slack.api.model.block.Blocks.*

class Sporforenvenn {

    @FunctionName("sporforenvenn")
    fun run(
            @HttpTrigger(
                    name = "req",
                    methods = [HttpMethod.GET, HttpMethod.POST],
                    authLevel = AuthorizationLevel.ANONYMOUS) request: HttpRequestMessage<Optional<String>>,
            context: ExecutionContext): HttpResponseMessage {

        context.logger.info("HTTP trigger processed a ${request.httpMethod.name} request.")

        val body = request.body
        context.logger.info(body.get())

        val text = splitSlackMessage(body.get())["text"];

        val channelId = "C01SGD5G1FU";
        val slack = Slack.getInstance();
        val slackToken = System.getenv("SLACK_TOKEN");

        val methods = slack.methods(slackToken)
        context.logger.info("SlackToken: $slackToken");

        var something: Records
        runBlocking {
            something = getAllNewQuestions()
        }

        println("svar utenfor runBlocking $something")
        try {
            val response = slack.methods(slackToken).chatPostMessage {
                    it
                        .channel(channelId)
                        .text(text)
            }

            // Print result, which includes information about the message (like TS)
            context.logger.info("result $response")
            return request
                .createResponseBuilder(HttpStatus.ACCEPTED)
                .body("Publisering velykket")
                .build();

        } catch (e: IOException) {
            context.logger.warning("error: $e")
        } catch (e: SlackApiException) {
            context.logger.warning("error: $e")
        }
        return request
            .createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body("Publisering gikk ikke bra")
            .build();
    }
}
