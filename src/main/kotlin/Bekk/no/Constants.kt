package Bekk.no

const val PREFIX: String = "sporforenvenn"

val AIRTABLE_API_KEY: String = "Bearer ${System.getenv("AIR_TABLE")}"

const val base: String = "https://api.airtable.com/v0/appcl9RjQFnGDH5H9/"

const val currentTable: String = "Sp%C3%B8r%20for%20en%20venn"
const val userTable: String = "users"

const val AIRTABLE_USER_TABLE: String = base + userTable
const val AIRTABLE_DATA_TABLE: String = base + currentTable
