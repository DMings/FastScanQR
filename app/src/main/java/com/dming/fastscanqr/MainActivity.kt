package com.dming.fastscanqr

import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceHolder.Callback
import com.google.zxing.BinaryBitmap
import com.google.zxing.ChecksumException
import com.google.zxing.FormatException
import com.google.zxing.NotFoundException
import com.google.zxing.common.GlobalHistogramBinarizer
import com.google.zxing.qrcode.QRCodeReader
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException


class MainActivity : AppCompatActivity() {

//    companion object {
//        val DECODE_HINTS: Map<DecodeHintType, Any> = mapOf(
//            DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE)
//        )
//    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        glSurfaceView.holder.addCallback(object : Callback {
            override fun surfaceChanged(
                holder: SurfaceHolder?,
                format: Int,
                width: Int,
                height: Int
            ) {
                Log.i("DMUI", "surfaceChanged")
            }

            override fun surfaceDestroyed(holder: SurfaceHolder?) {
                Log.i("DMUI", "surfaceDestroyed")
            }

            override fun surfaceCreated(holder: SurfaceHolder?) {
                Log.i("DMUI", "surfaceCreated")
            }

        })
        val h = CameraSize(1)
        glSurfaceView.setOnClickListener {
            try {
                val srcBitmap = BitmapFactory.decodeStream(assets.open("test_qr.png"))
                showQRImg.setImageBitmap(srcBitmap)
                val width = srcBitmap.width
                val height = srcBitmap.height
                val pixels = IntArray(width * height)
                srcBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
                val source = GLRGBLuminanceSource(width, height, pixels)
                val binaryBitmap = BinaryBitmap(GlobalHistogramBinarizer(source))
                val reader = QRCodeReader()
                val result = reader.decode(binaryBitmap)// 开始解析
                Log.i("DMUI", "result: ${result.text}")
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: NotFoundException) {
                e.printStackTrace()
            } catch (e: ChecksumException) {
                e.printStackTrace()
            } catch (e: FormatException) {
                e.printStackTrace()
            }
        }


    }
}
