package Bekk.no

// Airtable models
import Bekk.no.Models.Airtable.Get.Records
import Bekk.no.Models.Airtable.Users.UserRecords
import Bekk.no.Models.ResponseUrlBody.Delete
import com.microsoft.azure.functions.HttpRequestMessage

// Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.model.block.LayoutBlock
import com.slack.api.model.kotlin_extension.block.withBlocks
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


const val FILTER: String = "filterByFormula=AND(NOT({Publisert}), NOT({BlirIkkePublisert}))"
const val userFilter: String = "filterByFormula={Active}"

fun createMessage(message: String): List<LayoutBlock> {
    return withBlocks {
        section {
            markdownText("*$message*")
        }
        divider()
    }
}

fun publiserMessageToSlack(message: String, methods: MethodsClient, channelId: String) {
    methods.chatPostMessage {
        it
            .channel(channelId)
            .text(message)
            .blocks(createMessage(message))
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

    val create: Bekk.no.Models.Airtable.Create.Record = client.post(AIRTABLE_DATA_TABLE) {
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

suspend fun publiserMessageToSlackFromAirtables(
    id: String,
    response_url: String,
    methods: MethodsClient,
    httpClient: SlackHttpClient,
    channelId: String,
) {
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val record: Bekk.no.Models.Airtable.Get.Record = client.get("$AIRTABLE_DATA_TABLE/$id") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    publiserMessageToSlack(message = record.fields.sporsmal, methods, channelId)

    httpClient.postJsonBody(response_url, Delete(true))
    client.close()
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
    val response: Records = client.get("$AIRTABLE_DATA_TABLE?$FILTER") {
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

        client.patch<Bekk.no.Models.Airtable.Update.Record>("$AIRTABLE_DATA_TABLE/${valueToUpdate.id}") {
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

    val response: Records = client.get("$AIRTABLE_DATA_TABLE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    val records = response.records

    val sortedRecords = records
        .sortedBy { record -> record.fields.sendtInn }
        .take(10)

    methods.chatPostEphemeral {
        it
            .channel(channelId)
            .user(user)
            .text("Hvilken melding vil du sende til kanalen 'Spør for en venn'?")
            .blocks {
                header {
                    text("Hvilken melding vil du sende til kanalen 'Spør for en venn'?")
                }
                actions {
                    blockId("actions")
                    radioButtons {
                        options {
                            sortedRecords.mapNotNull { record ->
                                option {
                                    plainText(text = record.fields.sporsmal.take(150))
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

suspend fun checkIfMessageIsFromSlack(request: HttpRequestMessage<Optional<String>>, user: String, logger: Logger) {
    val slackData = request.body.get()
    val slackTimestamp: String = request.headers["x-slack-request-timestamp"]
        ?: throw RuntimeException("Cannot get slackTimeStamp from request")
    val slackSignature: String =
        request.headers["x-slack-signature"] ?: throw RuntimeException("Cannot get slack signature from request")
    val slackSigningBaseString = "v0:$slackTimestamp:$slackData"
    matchSignature(slackSigningBaseString, slackSignature, logger)

    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }

    val userRecords: UserRecords = client.get("$AIRTABLE_USER_TABLE?$userFilter") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    val users = userRecords.records.map { it.fields.userId }
    val isUserInActiveUserList = users.contains(user)

    if (!isUserInActiveUserList) {
        throw Exception("This user is not Authorized to use the slack bot to publish messages. $users $user")
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