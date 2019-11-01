package com.dming.fastscanqr

import android.content.Context
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.SurfaceHolder
import com.dming.fastscanqr.utils.DLog
import com.dming.fastscanqr.utils.EglHelper
import com.dming.fastscanqr.utils.FGLUtils
import java.nio.ByteBuffer
import java.util.concurrent.locks.ReentrantLock


class GLCameraManager {
    private val mCamera = Camera1()
    private val mCameraMatrix = FloatArray(16)
    //
    private lateinit var mGLThread: HandlerThread
    private lateinit var mGLHandler: Handler
    private lateinit var mPreviewFilter: IShader
    private lateinit var mLuminanceFilter: IShader
    private var mTextureId: Int = 0
    private val mEglHelper = EglHelper()
    //
    private lateinit var mPixelThread: HandlerThread
    private lateinit var mPixelHandler: PixelHandler
    private var mIsPixelCreate = false
    private val mPixelLock = ReentrantLock()
    //
    private var mWidth: Int = 0
    private var mHeight: Int = 0
    //
    private val mPixelEglHelper = EglHelper()

    private var mPixelSurface: Surface? = null
    private var mPixelSurfaceTexture: SurfaceTexture? = null
    //
    private var mFrameIds: IntArray? = null
    private var mPixelTexture = -1
    private lateinit var mPixelFilter: IShader
    //
    private var mScale: Float = 1.0f

    private var readQRCode: ((
        width: Int, height: Int,
        source: GLRGBLuminanceSource, grayByteBuffer: ByteBuffer
    ) -> Unit)? = null

    fun init(context: Context) {
        mGLThread = HandlerThread("GL")
        mPixelThread = HandlerThread("QR")
        mGLThread.start()
        mGLHandler = Handler(mGLThread.looper)
        mPixelThread.start()
        mPixelHandler = PixelHandler(mPixelThread.looper)
        mGLHandler.post {
            mCamera.init(context)
        }
    }

    fun surfaceCreated(context: Context, holder: SurfaceHolder?) {
        mGLHandler.post {
            mEglHelper.initEgl(null, holder!!.surface)
            mTextureId = FGLUtils.createOESTexture()
            mPreviewFilter = PreviewFilter(context)
            mLuminanceFilter = LuminanceFilter(context)
            mCamera.open(mTextureId)
            mCamera.getSurfaceTexture()?.setOnFrameAvailableListener {
                it.getTransformMatrix(mCameraMatrix)

//                val matrix = Matrix()
//                matrix.setValues(mCameraMatrix)
//                matrix.postScale(mScale,mScale)
//                matrix.getValues(mCameraMatrix)

                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                mFrameIds?.let { frameIds ->
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameIds[0])
                    mLuminanceFilter.setScaleMatrix(mScale)
                    mLuminanceFilter.onDraw(mTextureId, 0, 0, mWidth, mHeight, mCameraMatrix)
                    GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
                }
                //
                mPreviewFilter.setScaleMatrix(mScale)
                mPreviewFilter.onDraw(mTextureId, 0, 0, mWidth, mHeight, mCameraMatrix)
                mEglHelper.swapBuffers()
                it.updateTexImage()
                //
                parseQRCode()
            }
            mPixelHandler.post {
                mPixelTexture = FGLUtils.createOESTexture()
                mPixelSurfaceTexture = SurfaceTexture(mPixelTexture)
                mPixelSurface = Surface(mPixelSurfaceTexture)
                mPixelEglHelper.initEgl(mEglHelper.eglContext, mPixelSurface)
                mPixelFilter = PixelFilter(context)
                GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f)
                mPixelSurfaceTexture?.setOnFrameAvailableListener {
                    it.updateTexImage()
                }
            }
        }
    }

    fun onSurfaceChanged(width: Int, height: Int) {
        mWidth = width
        mHeight = height
        mScale = 1.0f
        mGLHandler.post {
            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (mFrameIds != null) {
                FGLUtils.deleteFBO(mFrameIds)
            }
            mFrameIds = FGLUtils.createFBO(width, height)
            //
            mCamera.surfaceChange(width, height)
            val cameraSize = mCamera.getCameraSize()
            DLog.i("cameraSize  width: ${cameraSize.width} height: ${cameraSize.height}")
            mPreviewFilter.onChange(cameraSize.width, cameraSize.height, width, height)
            mLuminanceFilter.onChange(cameraSize.width, cameraSize.height, width, height)
            //
            mPixelHandler.post {
                mPixelFilter.onChange(cameraSize.width, cameraSize.height, mWidth, mHeight)
                mPixelSurfaceTexture?.setDefaultBufferSize(mWidth, mHeight)
                GLES20.glViewport(0, 0, mWidth, mHeight)
                GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
                mIsPixelCreate = true
            }
        }
    }

    fun surfaceDestroyed() {
        mIsPixelCreate = false
        mPixelHandler.buffer = null
        mPixelSurfaceTexture?.setOnFrameAvailableListener(null)
        mPixelHandler.post {
            FGLUtils.deleteTexture(mPixelTexture)
            mPixelFilter.onDestroy()
            mPixelEglHelper.destroyEgl()
            mPixelSurfaceTexture?.release()
            mPixelSurface?.release()
        }
        mGLHandler.post {
            if (mFrameIds != null) {
                FGLUtils.deleteFBO(mFrameIds)
                mFrameIds = null
            }
            FGLUtils.deleteTexture(mTextureId)
            mCamera.close()
            mPreviewFilter.onDestroy()
            mLuminanceFilter.onDestroy()
            mEglHelper.destroyEgl()
        }
    }

    fun destroy() {
        mCamera.release()
        mGLThread.quit()
        mPixelThread.quit()
        mGLThread.join()
//        mPixelThread.join()
    }

    fun changeQRConfigure(
        top: Float,
        size: Float
    ) {
        mPixelHandler.post {
            mPixelHandler.setConfigure(top, size, mWidth, mHeight)
        }
    }

    fun onScaleChange(scale: Float) {
        mScale *= scale
        if (mScale < 1.0f) {
            mScale = 1.0f
        } else if (mScale > 3.0f) {
            mScale = 3.0f
        }
    }

    private fun parseQRCode() {
        mPixelLock.tryLock()
        mPixelHandler.post {
            if (mIsPixelCreate) {
                if (mFrameIds != null) {
                    mPixelFilter.onDraw(mFrameIds!![1], 0, 0, mWidth, mHeight, null)
                }
                mPixelEglHelper.swapBuffers()
                //
                try {
                    mPixelLock.lock()
                    if (mPixelHandler.width != 0 && mPixelHandler.height != 0) {
                        mPixelHandler.buffer?.let { byteBuffer ->
                            //                        val start = System.currentTimeMillis()
                            byteBuffer.position(0)
                            GLES20.glReadPixels(
                                mPixelHandler.left,
                                mPixelHandler.top,
                                mPixelHandler.width,
                                mPixelHandler.height,
                                GLES20.GL_RGBA,
                                GLES20.GL_UNSIGNED_BYTE,
                                byteBuffer
                            )
//                    DLog.d("mPixelHandler cost time: ${System.currentTimeMillis() - start}")
                            byteBuffer.rewind()
                            if (readQRCode != null && mPixelHandler.source != null) {
                                readQRCode!!(
                                    mPixelHandler.width,
                                    mPixelHandler.height,
                                    mPixelHandler.source!!,
                                    byteBuffer
                                )
                            }
//                    DLog.d("qr cost time: ${System.currentTimeMillis() - start}")
                        }
                    }
                } finally {
                    mPixelLock.unlock()
                }
            }
        }
        if (mPixelLock.isHeldByCurrentThread) {
            mPixelLock.unlock()
        }
    }

    fun setParseQRListener(
        readQRCode: (
            width: Int, height: Int,
            source: GLRGBLuminanceSource, grayByteBuffer: ByteBuffer
        ) -> Unit
    ) {
        this.readQRCode = readQRCode
    }

    fun setFlashLight(on: Boolean): Boolean {
        return mCamera.setFlashLight(on)
    }

    companion object {
        fun getViewConfigure(
            t: Float,
            s: Float,
            maxWidth: Int,
            maxHeight: Int
        ): Rect {
            var left = 0
            val top: Int
            val height: Int
            val width: Int
            val size: Int
            if (s == 0f) {
                val tt = if (t < 1) {
                    (maxHeight * t).toInt()
                } else {
                    t.toInt()
                }
                return Rect(left, tt, maxWidth, maxHeight)
            }
            val minSide = if (maxWidth > maxHeight) maxHeight else maxWidth
            size = if (s < 1) {
                (minSide * s).toInt()
            } else {
                s.toInt()
            }
            if (size > minSide) {
                width = minSide
                height = minSide
                left = 0
            } else {
                width = size
                height = size
                left = (minSide - size) / 2
            }
            top = if (maxWidth > maxHeight) { // 横屏
                left = (maxWidth - size) / 2
                (minSide - size) / 2
            } else {
                val tt = if (t < 1) maxHeight * t else t
                if (tt + size > maxHeight) (maxHeight - tt).toInt()
                else tt.toInt()
            }
            return Rect(left, top, left + width, top + height)
        }
    }


}