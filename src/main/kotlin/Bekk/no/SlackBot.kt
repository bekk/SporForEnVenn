package Bekk.no

// Airtable models
import Bekk.no.Models.Fields
import Bekk.no.Models.Record
import Bekk.no.Models.Records

// Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.SlackApiException
import com.slack.api.methods.kotlin_extension.request.chat.blocks

// ktor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*
import java.io.IOException
import java.lang.Exception
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter

val AIRTABLE_API_KEY: String = "Bearer ${System.getenv("AIR_TABLE")}"
val BASE: String = "https://api.airtable.com/v0/appcl9RjQFnGDH5H9/Sp%C3%B8r%20for%20en%20venn"

val FILTER: String = "filterByFormula=NOT({Publisert})"


fun sendOkToSlack(methods: MethodsClient, channelId: String, logger: Logger) {
    try {
        val response = methods.chatPostEphemeral {
            it
                .channel(channelId)
                .text("Vent litt mens vi henter noen av spørsmålene fra airtables :see_no_evil:")
        }
        if (response.isOk){
            println()
            println()
            println()
            println("Response $response")
            println()
            println()
            println()
        }
    } catch (e: IOException) {
        logger.warning("error: $e")
    } catch (e: SlackApiException) {
        logger.warning("error: $e")
    }

}
val client = HttpClient(CIO) {
    install(JsonFeature) {
        serializer = GsonSerializer()
    }
}

suspend fun publiserMessageToSlack(message: String, methods: MethodsClient, channelId: String, logger: Logger) {
    methods.chatPostMessage {
        it
            .channel(channelId)
            .text(message)
    }
    // Endre litt så vil dette sende en create til airtables med det publiserte spørsmålet
//    val update: Records = client.patch<Records>(BASE) {
//        headers {
//            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
//        }
//        contentType(ContentType.Application.Json)
//        body = {
//            Records(records = listOf(Record(fields = Fields(spørsmål = message))))
//        }
//    }
}

suspend fun publiserMessageToSlackAndUpdateAirtables(message: Record, methods: MethodsClient, channelId: String, logger: Logger) {
    // Den skal være publisert om vi sender en publisering til airtables
    message.fields.publisert = true

    val update: Records = client.patch<Records>(BASE) {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
        contentType(ContentType.Application.Json)
        body = {
            Records(records = listOf(message))
        }
    }

    publiserMessageToSlack(message = message.fields.spørsmål, methods, channelId, logger)
}

suspend fun askWichMessageToPublish(methods: MethodsClient, channelId: String, logger: Logger) {
    val response: Records = client.get<Records>("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }
    val records = response.records;

    records
        .sortedBy { record -> record.fields.sendtInn }
        .take(5);


    methods.chatPostMessage {
        it
            .channel(channelId)
            .blocks {
                header {
                    text("Hvilken melding vil du sende til kanalen 'Spør for en venn'?")
                }
                actions {
                    radioButtons {
                        options {
                            records.map { record ->
                                option {
                                    text(type = "plain_text", text = record.fields.spørsmål)
                                    value(record.id)
                                }

                            }
                        }
                        actionId("VelgHvaSomSkalPubliseres")
                    }
                }
            }
    }
}

suspend fun getAllNewQuestions(): Records {
    var records: Records = client.get<Records>("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    var listRecords = records.records.toMutableList();
    listRecords[0].fields.publisert = true;


    val update: Records = client.patch<Records>(BASE) {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
        contentType(ContentType.Application.Json)
        body = records.copy(records = listRecords)
    }
    client.close()
    println()
    println()
    println("get from air table $records")
    println("update from air table $records")
    println()
    println()

    return records.copy(records = listRecords)
}

fun splitSlackMessage(slackMessage: String): Map<String, String> {
    return slackMessage.split("&").map {
        it.split(
            "="
        )[0] to it.split("=")[1]
    }.toMap()
}




//https://api.slack.com/docs/verifying-requests-from-slack#a_recipe_for_security
fun matchSignature(slackTimestamp: String, slackSigningBaseString: String, slackSignature: String, logger: Logger) {
    val signingSecret: String = System.getenv("SLACK_SIGNING_SECRET")
        ?: throw RuntimeException("SLACK_SIGNING_SECRET environment variable has not been configured")
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(signingSecret.toByteArray(), "HmacSHA256"))
    val hash = mac.doFinal(slackSigningBaseString.toByteArray())
    val hexSignature = DatatypeConverter.printHexBinary(hash)
    if ("v0=" + hexSignature.toLowerCase() != slackSignature) {
        logger.severe("Signature matching issue")
        throw RuntimeException("Signature matching failed. Can be invoked only from Slack")
    }
}