package `fun`.wqiang.swiper
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream


class CommonUtils {
    companion object {
        fun fetchPkgIconB64(context: Context, pkg: String) :String{
            try {
                val drawable = context.packageManager.getApplicationIcon(pkg)
                return  drawableToBase64(drawable)
            } catch (e: Exception) {
                e.printStackTrace()
                return ""
            }
        }

        private fun drawableToBitmap(drawable: Drawable): Bitmap {
            // Check if the drawable has valid dimensions
            if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
                // Create a single-color bitmap of 1x1 pixel if dimensions are invalid
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
            // Create a new bitmap with the same dimensions as the drawable
            val bitmap = Bitmap.createBitmap(
                drawable.intrinsicWidth,
                drawable.intrinsicHeight,
                Bitmap.Config.ARGB_8888
            )
            // Create a canvas to draw on the bitmap
            val canvas = android.graphics.Canvas(bitmap)
            // Set the bounds of the drawable to match the canvas
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            // Draw the drawable onto the canvas
            drawable.draw(canvas)
            return bitmap
        }

        private fun drawableToBase64(drawable: Drawable): String {
            val bitmap = drawableToBitmap(drawable)
            val byteArrayOutputStream =
                ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteArrayOutputStream) // You can change the format and quality here
            val byteArray = byteArrayOutputStream.toByteArray()
            return Base64.encodeToString(byteArray,
                Base64.DEFAULT)
        }

        fun getScreenWidth(context: Context): Int {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return windowManager.currentWindowMetrics.bounds.width()
            } else {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                return displayMetrics.widthPixels
            }
        }

        fun getScreenHeight(context: Context): Int {
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                return windowManager.currentWindowMetrics.bounds.height()
            } else {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getMetrics(displayMetrics)
                return displayMetrics.heightPixels
            }
        }
    }
}