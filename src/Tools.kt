import java.awt.image.BufferedImage
import java.io.InputStream
import javax.imageio.ImageIO

class Tools {
    companion object {
        fun loadJarData(res: String): BufferedImage {
            val `in`: InputStream? = Companion::class.java.classLoader.getResourceAsStream(res)
            val satori = ImageIO.read(`in`)
            `in`?.close()
            return satori
        }
    }
}