package bot.chat

import kotlinx.serialization.Polymorphic
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChatResponse(val message: String)

@Serializable
data class ChatRequest(
    val model: String,
    val input: String,
    @SerialName("previous_response_id")
    val previousResponseId: String?
)

@Serializable
data class ChatApiResponse(
    val output: List<OutputItem>,
    @SerialName("response_id")
    val responseId: String
)

@Serializable
@Polymorphic
sealed class OutputItem

@Serializable
@SerialName("message")
data class MessageOutput(val content: String) : OutputItem()

@Serializable
@SerialName("tool_call")
@Suppress("unused")
class ToolCall : OutputItem()

@Serializable
@SerialName("reasoning")
@Suppress("unused")
class Reasoning : OutputItem()

@Serializable
@SerialName("invalid_tool_call")
@Suppress("unused")
class InvalidCall : OutputItem()

@Serializable
@Polymorphic
sealed class Model

@Serializable
@SerialName("llm")
data class LLModel(
    val key: String,
    @SerialName("loaded_instances")
    val loadedInstances: List<Instance>
) : Model()

@Serializable
data class Instance(val id: String)

@Serializable
@SerialName("embedding")
@Suppress("unused")
class EmbeddingModel : Model()