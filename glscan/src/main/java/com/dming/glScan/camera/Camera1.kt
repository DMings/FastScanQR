package com.dming.glScan.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.view.Surface
import android.view.WindowManager


/**
 * 使用弃用的camera1作为摄像头的控制
 */
@Suppress("DEPRECATION")
class Camera1 : BaseCamera(), ICamera {
    private var mCameraId: Int = 0
    private var mCamera: Camera? = null
    private lateinit var mCameraParameters: Camera.Parameters
    private val mCameraInfo = Camera.CameraInfo()
    private var mSurfaceTexture: SurfaceTexture? = null
    private var mContext: Context? = null
    private var mFlashModes: List<String>? = null

    override fun init(context: Context) {
        mContext = context
        var i = 0
        val count = Camera.getNumberOfCameras()
        while (i < count) {
            Camera.getCameraInfo(i, mCameraInfo)
            if (mCameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                mCameraId = i
                return
            }
            i++
        }
        mCameraId = -1
    }

    /**
     * 开启摄像头，获取参数、图像尺寸列表，设置纹理
     */
    override fun open(textureId: Int) {
//        DLog.i("mCameraId: $mCameraId")
        mSurfaceTexture = SurfaceTexture(textureId)
//        val start = System.currentTimeMillis()
        mCamera = Camera.open(mCameraId)
        mCameraParameters = mCamera!!.parameters
        mFlashModes = mCameraParameters.supportedFlashModes
        mPreviewSizes.clear()
        for (size in mCameraParameters.supportedPreviewSizes) {
            mPreviewSizes.add(CameraSize(size.width, size.height))
        }
        mCamera?.setPreviewTexture(mSurfaceTexture)
//        DLog.d("openCamera cost time: ${System.currentTimeMillis() - start}")
    }

    /**
     * 设置SurfaceTexture大小，设置预览参数
     */
    override fun surfaceChange(width: Int, height: Int) {
        mViewWidth = width
        mViewHeight = height
        mSurfaceTexture?.setDefaultBufferSize(width, height)
        adjustCameraParameters(width, height)
    }

    override fun release() {
        //
    }

    override fun getSurfaceTexture(): SurfaceTexture? {
        return mSurfaceTexture
    }

    override fun getCameraSize(): CameraSize {
        return mCameraSize
    }

    /**
     * 设置预览尺寸参数
     */
    private fun adjustCameraParameters(width: Int, height: Int) {
        val degree = getCameraRotation(mCameraInfo)
        Camera.getCameraInfo(mCameraId, mCameraInfo)
        dealCameraSize(width, height, degree)
        val size = mCameraSize.srcSize
        mCamera?.let {
            it.stopPreview()
            it.setDisplayOrientation(degree)
        }
        mCameraParameters.setPreviewSize(size.width, size.height)
        setAutoFocusInternal()
        mCamera?.let {
            it.parameters = mCameraParameters
            it.startPreview()
        }
    }

    /**
     * 获取根据屏幕处理后的旋转角度，矫正后的角度
     */
    private fun getCameraRotation(info: Camera.CameraInfo): Int {
        val rotation =
            if (mContext != null)
                (mContext!!.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                    .defaultDisplay.rotation
            else 0
        var degree = 0
        when (rotation) {
            Surface.ROTATION_0 -> degree = 0
            Surface.ROTATION_90 -> degree = 90
            Surface.ROTATION_180 -> degree = 180
            Surface.ROTATION_270 -> degree = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degree) % 360
            result = (360 - result) % 360   // compensate the mirror
        } else {
            // back-facing
            result = (info.orientation - degree + 360) % 360
        }
//        DLog.i("result: $result")
        return result
    }

    /**
     * 设置自动聚焦
     */
    private fun setAutoFocusInternal(): Boolean {
        return if (mCamera != null) {
            val modes = mCameraParameters.supportedFocusModes
            when {
                modes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE) ->
                    mCameraParameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                modes.contains(Camera.Parameters.FOCUS_MODE_FIXED) ->
                    mCameraParameters.focusMode = Camera.Parameters.FOCUS_MODE_FIXED
                modes.contains(Camera.Parameters.FOCUS_MODE_INFINITY) ->
                    mCameraParameters.focusMode = Camera.Parameters.FOCUS_MODE_INFINITY
                else -> mCameraParameters.focusMode = modes[0]
            }
            true
        } else {
            false
        }
    }

    /**
     * 设置闪光灯是否开启
     */
    override fun setFlashLight(on: Boolean): Boolean {
        mCamera?.let {
            val mode = if (on) Camera.Parameters.FLASH_MODE_TORCH else Camera.Parameters.FLASH_MODE_OFF
            if (mFlashModes != null && mFlashModes!!.contains(mode)) {
                mCameraParameters.flashMode = mode
                it.parameters = mCameraParameters
                return true
            }
        }
        return false
    }

    /**
     * 关闭资源释放
     */
    override fun close() {
        mSurfaceTexture?.setOnFrameAvailableListener(null)
        mCamera?.release()
        mSurfaceTexture?.release()
        mSurfaceTexture = null
    }

}