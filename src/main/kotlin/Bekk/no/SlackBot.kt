package Bekk.no

// Airtable models
import Bekk.no.Models.Records
import Bekk.no.Models.ResponseUrlBody.Delete
import Bekk.no.Models.UpdateAirtable.Fields
import Bekk.no.Models.UpdateAirtable.UpdateRecord
import Bekk.no.Models.UpdateAirtable.UpdateRecords
import com.microsoft.azure.functions.HttpRequestMessage
import com.slack.api.Slack

// Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.methods.kotlin_extension.request.chat.blocks

// ktor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.features.*
import io.ktor.http.*
import java.net.URLDecoder.decode
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

val authorizedUsers = listOf(juneID, jorundAmsenID, steffenID, andersID, steffenTestID)


val AIRTABLE_API_KEY: String = "Bearer ${System.getenv("AIR_TABLE")}"
val BASE: String = "https://api.airtable.com/v0/appcl9RjQFnGDH5H9/Sp%C3%B8r%20for%20en%20venn"

val FILTER: String = "filterByFormula=NOT({Publisert})"

suspend fun publiserMessageToSlack( message: String, methods: MethodsClient, channelId: String, logger: Logger) {
    val decodedMessage = decode(message, "UTF-8")
    methods.chatPostMessage {
        it
            .channel(channelId)
            .text(decodedMessage)
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

suspend fun publiserMessageToSlackAndUpdateAirtables(
    id: String,
    response_url: String,
    slack: Slack,
    channelId: String,
    logger: Logger
) {
    val methods = slack.methods()
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val response: Records = client.get<Records>("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }
    val records = response.records

    val valueToUpdate = records.find {
        it.id == id
    } ?: throw NotFoundException("Kunne ikke finne spørsmålet i airtables")

    if(!valueToUpdate.fields.publisert) {
        val recordsToUpdate: UpdateRecords = UpdateRecords(listOf(UpdateRecord(Fields(true, valueToUpdate.fields.spørsmål), id = valueToUpdate.id)))

        client.patch<UpdateRecords>(BASE) {
            headers {
                append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
            }
            contentType(ContentType.Application.Json)
            body = recordsToUpdate
        }

        publiserMessageToSlack(message = valueToUpdate.fields.spørsmål, methods, channelId, logger)
    }

    slack.httpClient.postJsonBody(response_url, Delete(true))
    client.close()
}

suspend fun askWhichMessageToPublish(slackData: String, methods: MethodsClient, channelId: String, logger: Logger) {
    val client = HttpClient(CIO) {
        install(JsonFeature) {
            serializer = GsonSerializer()
        }
    }
    val user = splitSlackMessage(slackData)["user_id"] ?: throw RuntimeException("Cannot get user from the slash comand")
    val response: Records = client.get<Records>("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }

    val records = response.records

    records
        .sortedBy { record -> record.fields.sendtInn }
        .take(5)

    val noe = methods.chatPostEphemeral() {
            it
                .channel(
                    if(!channelId.startsWith("C"))
                        user
                    else
                        channelId
                )
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
                                records.map { record ->
                                    option {
                                        plainText(text = record.fields.spørsmål)
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

fun checkIfMessageIsFromSlack(request: HttpRequestMessage<Optional<String>>, user: String, logger: Logger){
    val slackData = request.body.get()
    val slackTimestamp: String = request.headers["x-slack-request-timestamp"]
        ?: throw RuntimeException("Cannot get slackTimeStamp from request")
    val slackSignature: String =
        request.headers["x-slack-signature"] ?: throw RuntimeException("Cannot get slack signature from request")
    val slackSigningBaseString = "v0:$slackTimestamp:$slackData"
    matchSignature(slackSigningBaseString, slackSignature, logger)

    if (!authorizedUsers.contains(user)){
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