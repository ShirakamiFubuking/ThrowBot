import com.luciad.imageio.webp.WebPWriteParam
import org.drinkless.tdlib.TdApi
import org.drinkless.tdlib.TdApi.*
import java.awt.image.BufferedImage
import java.io.File
import java.lang.Thread.sleep
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.stream.FileImageOutputStream


val mask = ImageIO.read(File("p_mask.png"))
val satori = ImageIO.read(File("p.png"))

fun deepCopy(bi: BufferedImage): BufferedImage {
    val cm = bi.colorModel
    val isAlphaPremultiplied = cm.isAlphaPremultiplied
    val raster = bi.copyData(null)
    return BufferedImage(cm, raster, isAlphaPremultiplied, null)
}

fun drawThrow(file: File, usr: BufferedImage) {
    // 縮放並旋轉user 180度
    val rotateUsr = run {
        val w = mask.width
        val h = mask.height
        val resize = BufferedImage(w, h, usr.type)
        // usr縮放到同mask
        if (usr.width != mask.width) {
            for (i in 0 until w) {
                for (j in 0 until h) {
                    resize.setRGB(i, j, usr.getRGB(i * usr.width / w, j * usr.height / h)) // 內差法
                }
            }
        }
        val rotate = BufferedImage(w, h, usr.type)
        // 旋轉 180
        for (i in 0 until w) {
            for (j in 0 until h) {
                rotate.setRGB(i, j, resize.getRGB(w - i - 1, h - j - 1))
            }
        }
        rotate
    }
    // 套上mask合併至satori
    val satori = run {
        val offsetX = 19
        val offsetY = 181
        // 複製一份satori (好像不用複製，因使用者均會被覆蓋)
        val satori = deepCopy(satori)
        for (i in 0 until mask.width) {
            for (j in 0 until mask.height) {
                if (mask.getRGB(i, j) and 0xffffff == 0xffffff) { // 白色(0xffffff)替換為usr
                    satori.setRGB(i + offsetX, j + offsetY, mask.getRGB(i, j) and rotateUsr.getRGB(i, j))
                }
            }
        }
        satori
    }
    // 轉換webp
    val writer = ImageIO.getImageWritersByMIMEType("image/webp").next()
    val writeParam = WebPWriteParam(writer.locale)
    writeParam.compressionMode = WebPWriteParam.MODE_DEFAULT
    writer.output = FileImageOutputStream(file)
    writer.write(null, IIOImage(satori, null, null), writeParam)
    (writer.output as FileImageOutputStream).close()
}

fun main(args: Array<String>) {
    // 載入外部函式庫tdjni
    try {
        System.loadLibrary("tdjni")
    } catch (e: UnsatisfiedLinkError) {
        e.printStackTrace()
    }
    val bot = Bot(args[0].toInt(), args[1], args[2], "")
    val throwMap = mutableMapOf<String, Int>() // <userPhotoId, fileId>
    bot.addCommandListener(object : CommandListener {
        override fun onCommand(msg: Message, chatId: Long, senderId: Int, msgId: Long, command: String, arg: String) {
            when (command) {
                "/throw" -> {
                    val sendPic = fun(id: Int) {
                        // 取得使用者頭像
                        bot.client.send(GetUserProfilePhotos(id, 0, 1)) {
                            val chatPhotos = it as ChatPhotos
                            val tdFile = chatPhotos.photos[0].sizes[0].photo
                            val file = File("$id.webp")
                            throwMap[tdFile.remote.uniqueId]?.let { id ->
                                val content = InputMessageSticker(InputFileId(id), null, 512, 512)
                                bot.client.send(SendMessage(chatId, 0, msgId, null, null, content), null, null)
                                return@send
                            }
                            val uploadAndSend = fun(file: File) {
                                val inputFile = InputFileLocal(file.path)
                                val content = InputMessageSticker(inputFile, null, 512, 512)
                                bot.client.send(SendMessage(chatId, 0, msgId, null, null, content), { r ->
                                    throwMap[tdFile.remote.uniqueId] =
                                        ((((r as Message).content as MessageSticker).sticker as Sticker).sticker as TdApi.File).id
                                    file.delete()
                                }, null)
                            }
                            // 首次生成
                            if (tdFile.local.isDownloadingCompleted) {
                                drawThrow(file, ImageIO.read(File(tdFile.local.path)))
                                uploadAndSend(file)
                                return@send
                            }
                            bot.client.send(DownloadFile(tdFile.id, 1, 0, 0, true)) { userPhoto ->
                                val f = userPhoto as TdApi.File
                                if (f.local.isDownloadingCompleted) {
                                    drawThrow(file, ImageIO.read(File(f.local.path)))
                                    uploadAndSend(file)
                                }
                            }
                        }
                    }
                    // 有回復，丟被回覆者；無回覆丟自己
                    if (msg.replyToMessageId.toString() == "0") {
                        sendPic(senderId)
                    } else {
                        bot.client.send(GetRepliedMessage(chatId, msgId)) {
                            sendPic(((it as Message).sender as MessageSenderUser).userId)
                        }
                    }
                }
            }
        }
    })
    bot.start()
    while (!bot.isClosed()) {
        sleep(1000)
    }
}