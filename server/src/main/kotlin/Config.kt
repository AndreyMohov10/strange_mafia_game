import kotlinx.serialization.Serializable

@Serializable
data class Config(
    val host: String,
    val token: String,
    val port: Int
)