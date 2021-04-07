package Bekk.no

// Airtable models
import Bekk.no.Models.Airtable.Get.Records
import Bekk.no.Models.ResponseUrlBody.Delete
import com.microsoft.azure.functions.HttpRequestMessage

// Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.util.http.SlackHttpClient

// ktor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import java.util.*
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.xml.bind.DatatypeConverter

const val juneID = "U0452UJA1"
const val jorundAmsenID = "U6HG03FSB"
const val steffenID = "ULX53HFMF"
const val steffenTestID = "U01EEHXK0GY"
const val andersID = "UB91B9UJY"
const val espenID = "U02HEARQ0"

val authorizedUsers = listOf(juneID, jorundAmsenID, steffenID, andersID, steffenTestID, espenID)


val AIRTABLE_API_KEY: String = "Bearer ${System.getenv("AIR_TABLE")}"
const val BASE: String = "https://api.airtable.com/v0/appcl9RjQFnGDH5H9/Sp%C3%B8r%20for%20en%20venn"

const val FILTER: String = "filterByFormula=NOT({Publisert})"

fun publiserMessageToSlack(message: String, methods: MethodsClient, channelId: String) {
    methods.chatPostMessage {
        it
            .channel(channelId)
            .text(message)
    }
}

suspend fun publiserMessageToSlackAndCreate(
    message: String,
    methods: MethodsClient,
    channelId: String,
    logger: Logger
) {
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val nyRecord =
        Bekk.no.Models.Airtable.Create.Record(fields = Bekk.no.Models.Airtable.Create.Fields(sporsmal = message))

    val create: Bekk.no.Models.Airtable.Create.Record = client.post(BASE) {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
        contentType(ContentType.Application.Json)
        body = nyRecord
    }
    client.close()

    logger.info("Created $create")
    publiserMessageToSlack(message, methods, channelId)
}

suspend fun publiserMessageToSlackAndUpdateAirtables(
    id: String,
    response_url: String,
    methods: MethodsClient,
    httpClient: SlackHttpClient,
    channelId: String,
    logger: Logger
) {
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val response: Records = client.get("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }
    val records = response.records

    val valueToUpdate = records.find {
        it.id == id
    } ?: throw NotFoundException("Kunne ikke finne spørsmålet i airtables")

    if (!valueToUpdate.fields.publisert) {
        val updatedRecord = Bekk.no.Models.Airtable.Update.Record(
            fields = Bekk.no.Models.Airtable.Update.Fields(
                valueToUpdate.fields.sporsmal
            )
        )

        client.patch<Bekk.no.Models.Airtable.Update.Record>("$BASE/${valueToUpdate.id}") {
            headers {
                append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
            }
            contentType(ContentType.Application.Json)
            body = updatedRecord
        }
        logger.info("Publiserer melidingen: ${valueToUpdate.fields.sporsmal} to channel with id: $channelId")
        publiserMessageToSlack(message = valueToUpdate.fields.sporsmal, methods, channelId)
    }

    httpClient.postJsonBody(response_url, Delete(true))
    client.close()
}

suspend fun askWhichMessageToPublish(user: String, methods: MethodsClient, channelId: String) {
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val response: Records = client.get("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    val records = response.records

    val sortedRecords = records
        .sortedBy { record -> record.fields.sendtInn }

    methods.chatPostEphemeral {
        it
            .channel(channelId)
            .user(user)
            .blocks {
                section {
                    plainText("Work in progress")
                }
                header {
                    text("Hvilken melding vil du sende til kanalen 'Spør for en venn'?")
                }
                actions {
                    blockId("actions")
                    radioButtons {
                        options {
                            sortedRecords.map { record ->
                                option {
                                    plainText(text = record.fields.sporsmal)
                                    value(record.id)
                                }
                            }
                        }
                        actionId("VelgHvaSomSkalPubliseres")
                    }
                    button {
                        text("Publiser")
                        style("primary")
                        value("publiser")
                        actionId("publiser")
                    }
                }
            }
    }
    client.close()
}

fun checkIfMessageIsFromSlack(request: HttpRequestMessage<Optional<String>>, user: String, logger: Logger) {
    val slackData = request.body.get()
    val slackTimestamp: String = request.headers["x-slack-request-timestamp"]
        ?: throw RuntimeException("Cannot get slackTimeStamp from request")
    val slackSignature: String =
        request.headers["x-slack-signature"] ?: throw RuntimeException("Cannot get slack signature from request")
    val slackSigningBaseString = "v0:$slackTimestamp:$slackData"
    matchSignature(slackSigningBaseString, slackSignature, logger)

    if (!authorizedUsers.contains(user)) {
        throw Exception("This user is not Authorized to use the slack bot to publish messages.")
    }
}

fun splitSlackMessage(slackMessage: String): Map<String, String> {
    return slackMessage.split("&").map {
        it.split(
            "="
        )[0] to it.split("=")[1]
    }.toMap()
}


//https://api.slack.com/docs/verifying-requests-from-slack#a_recipe_for_security
fun matchSignature(slackSigningBaseString: String, slackSignature: String, logger: Logger) {
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