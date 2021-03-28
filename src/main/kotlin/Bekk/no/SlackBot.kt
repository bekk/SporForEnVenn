package Bekk.no

// Airtable models
import Bekk.no.Models.Records

// Slack
import com.slack.api.methods.MethodsClient
import com.slack.api.Slack
import com.slack.api.model.block.Blocks.*
import com.slack.api.methods.kotlin_extension.request.chat.blocks
import com.slack.api.model.block.composition.*
import com.slack.api.model.block.composition.BlockCompositions.markdownText
import com.slack.api.model.block.element.*
import com.slack.api.model.block.element.BlockElements.button

// ktor
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.features.json.*
import io.ktor.client.request.*
import io.ktor.http.*

val AIRTABLE_API_KEY: String = "Bearer ${System.getenv("AIR_TABLE")}"
val BASE: String = "https://api.airtable.com/v0/appcl9RjQFnGDH5H9/Sp%C3%B8r%20for%20en%20venn"

val FILTER: String = "filterByFormula=NOT({Publisert})"

suspend fun askWichMessageToPublish(methods: MethodsClient, channelId: String){
    val client = HttpClient(CIO) {
        install(JsonFeature){
            serializer = GsonSerializer()
        }
    }
    val response: Records = client.get<Records>("$BASE?$FILTER") {
        headers {
            append(HttpHeaders.Authorization, AIRTABLE_API_KEY)
        }
    }
    val records = response.records;

    records
        .sortedBy { record -> record.fields.sendtInn }
        .take(5);


    val response = methods.chatPostMessage {
        it
            .channel(channelId)
            .blocks {
                section {

                }
            }
    }
}

suspend fun getAllNewQuestions(): Records {
    val client = HttpClient(CIO) {
        install(JsonFeature){
            serializer = GsonSerializer()
        }
    }

    println(AIRTABLE_API_KEY)

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

fun splitSlackMessage (slackMessage:String): Map<String, String> {
    return slackMessage.split("&").map {
        it.split(
            "="
        )[0] to it.split("=")[1]
    }.toMap()
}

