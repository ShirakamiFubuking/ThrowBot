import org.drinkless.tdlib.TdApi.Message

interface CommandListener {
    //    fun onCommand(message: Message, command: String, arg: String)
    fun onCommand(msg: Message, command: String, arg: String)
}