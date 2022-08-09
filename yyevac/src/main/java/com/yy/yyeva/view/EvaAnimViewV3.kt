package com.yy.yyeva.view

import android.content.Context
import android.content.res.AssetManager
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import com.yy.yyeva.EvaAnimConfig
import com.yy.yyeva.EvaAnimPlayer
import com.yy.yyeva.file.EvaAssetsEvaFileContainer
import com.yy.yyeva.file.EvaFileContainer
import com.yy.yyeva.file.IEvaFileContainer
import com.yy.yyeva.inter.IEvaAnimListener
import com.yy.yyeva.inter.IEvaFetchResource
import com.yy.yyeva.inter.OnEvaResourceClickListener
import com.yy.yyeva.util.*
import java.io.File

open class EvaAnimViewV3 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0):
    IEvaAnimView,
    FrameLayout(context, attrs, defStyleAttr),
    TextureView.SurfaceTextureListener {

    companion object {
        private const val TAG = "${EvaConstant.TAG}.AnimView"
    }
    private lateinit var playerEva: EvaAnimPlayer

    private val uiHandler by lazy { Handler(Looper.getMainLooper()) }
    private var surface: SurfaceTexture? = null
    private var s: Surface? = null
    private var evaAnimListener: IEvaAnimListener? = null
    private var innerTextureView: InnerTextureView? = null
    private var lastEvaFile: IEvaFileContainer? = null
    private val scaleTypeUtil = ScaleTypeUtil()

    // 代理监听
    private val animProxyListener by lazy {
        object : IEvaAnimListener {

            override fun onVideoConfigReady(config: EvaAnimConfig): Boolean {
                scaleTypeUtil.setVideoSize(config.width, config.height)
                return evaAnimListener?.onVideoConfigReady(config) ?: super.onVideoConfigReady(config)
            }

            override fun onVideoStart() {
                evaAnimListener?.onVideoStart()
            }

            override fun onVideoRender(frameIndex: Int, config: EvaAnimConfig?) {
                evaAnimListener?.onVideoRender(frameIndex, config)
            }

            override fun onVideoComplete() {
                hide()
                evaAnimListener?.onVideoComplete()
            }

            override fun onVideoDestroy() {
                hide()
                evaAnimListener?.onVideoDestroy()
            }

            override fun onFailed(errorType: Int, errorMsg: String?) {
                evaAnimListener?.onFailed(errorType, errorMsg)
            }

        }
    }

    // 保证AnimView已经布局完成才加入TextureView
    private var onSizeChangedCalled = false
    private var needPrepareTextureView = false
    private val prepareTextureViewRunnable = Runnable {
        removeAllViews()
        innerTextureView = InnerTextureView(context).apply {
            playerEva = this@EvaAnimViewV3.playerEva
            surfaceTextureListener = this@EvaAnimViewV3
            layoutParams = scaleTypeUtil.getLayoutParam(this)
        }
//        playerEva.decoder?.renderThread?.handler?.post {
//            s = Surface(innerTextureView!!.surfaceTexture)
//            val textureId = EvaJniUtil.initRender(s!!, false)
//            if (textureId < 0) {
//                Log.e(TAG, "surfaceCreated init OpenGL ES failed!")
//            } else {
//                innerTextureView?.setSurfaceTexture(SurfaceTexture(textureId))
//            }
//        }
        addView(innerTextureView)
    }

    private val updateTextureLayout = Runnable {
        innerTextureView?.let {
            val lp = scaleTypeUtil.getLayoutParam(it)
            it.layoutParams = lp
        }
    }

    init {
        hide()
        playerEva = EvaAnimPlayer(this)
        playerEva.evaAnimListener = animProxyListener
    }

    override fun updateTextureViewLayout() {
        uiHandler.post(updateTextureLayout)
    }

    override fun prepareTextureView() {
        if (onSizeChangedCalled) {
            uiHandler.post(prepareTextureViewRunnable)
        } else {
            ELog.e(TAG, "onSizeChanged not called")
            needPrepareTextureView = true
        }
    }

    override fun getSurfaceTexture(): SurfaceTexture? {
//        return innerTextureView?.surfaceTexture ?: surface
        return surface
    }

    override fun getSurface(): Surface? {
        return s
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        ELog.i(TAG, "onSurfaceTextureSizeChanged $width x $height")
        playerEva.onSurfaceTextureSizeChanged(width, height)
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        ELog.i(TAG, "onSurfaceTextureDestroyed")
        playerEva.onSurfaceTextureDestroyed()
        uiHandler.post {
            innerTextureView?.surfaceTextureListener = null
            innerTextureView = null
            removeAllViews()
        }
        return !belowKitKat()
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        ELog.i(TAG, "onSurfaceTextureAvailable width=$width height=$height")

        playerEva.decoder?.renderThread?.handler?.post {
            s = Surface(surface)
            val textureId = EvaJniUtil.initRender(s!!, false)
            if (textureId < 0) {
                Log.e(TAG, "surfaceCreated init OpenGL ES failed!")
            } else {
                this.surface = SurfaceTexture(textureId)
            }
        }
        playerEva.onSurfaceTextureAvailable(width, height)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        ELog.i(TAG, "onSizeChanged w=$w, h=$h")
        scaleTypeUtil.setLayoutSize(w, h)
        onSizeChangedCalled = true
        // 需要保证onSizeChanged被调用
        if (needPrepareTextureView) {
            needPrepareTextureView = false
            prepareTextureView()
        }
    }

    override fun onAttachedToWindow() {
        ELog.i(TAG, "onAttachedToWindow")
        super.onAttachedToWindow()
        playerEva.isDetachedFromWindow = false
        // 自动恢复播放
        if (playerEva.playLoop > 0) {
            lastEvaFile?.apply {
                startPlay(this)
            }
        }
    }

    override fun onDetachedFromWindow() {
        ELog.i(TAG, "onDetachedFromWindow")
        super.onDetachedFromWindow()
        if (belowKitKat()) {
            release()
        }
        playerEva.isDetachedFromWindow = true
        playerEva.onSurfaceTextureDestroyed()
    }


    override fun setAnimListener(evaAnimListener: IEvaAnimListener?) {
        this.evaAnimListener = evaAnimListener
    }

    override fun setFetchResource(evaFetchResource: IEvaFetchResource?) {
        playerEva.pluginManager.getMixAnimPlugin()?.resourceRequestEva = evaFetchResource
    }

    override fun setOnResourceClickListener(evaResourceClickListener: OnEvaResourceClickListener?) {
        playerEva.pluginManager.getMixAnimPlugin()?.evaResourceClickListener = evaResourceClickListener
    }

    /**
     * 兼容方案，优先保证表情显示
     */
    open fun enableAutoTxtColorFill(enable: Boolean) {
        playerEva.pluginManager.getMixAnimPlugin()?.autoTxtColorFill = enable
    }

    override fun setLoop(playLoop: Int) {
        playerEva.playLoop = playLoop
    }

    override fun supportMask(isSupport : Boolean, isEdgeBlur : Boolean) {
        playerEva.supportMaskBoolean = isSupport
        playerEva.maskEdgeBlurBoolean = isEdgeBlur
    }

    @Deprecated("Compatible older version mp4, default false")
    fun enableVersion1(enable: Boolean) {
        playerEva.enableVersion1 = enable
    }

    // 兼容老版本视频模式
    @Deprecated("Compatible older version mp4")
    fun setVideoMode(mode: Int) {
        playerEva.videoMode = mode
    }

    override fun setFps(fps: Int) {
        ELog.i(TAG, "setFps=$fps")
        playerEva.defaultFps = fps
    }

    override fun setScaleType(type : ScaleType) {
        scaleTypeUtil.currentScaleType = type
    }

    override fun setScaleType(scaleType: IScaleType) {
        scaleTypeUtil.scaleTypeImpl = scaleType
    }

    /**
     * @param isMute true 静音
     */
    override fun setMute(isMute: Boolean) {
        ELog.e(TAG, "set mute=$isMute")
        playerEva.isMute = isMute
    }

    override fun startPlay(file: File) {
        try {
            val fileContainer = EvaFileContainer(file)
            startPlay(fileContainer)
        } catch (e: Throwable) {
            animProxyListener.onFailed(EvaConstant.REPORT_ERROR_TYPE_FILE_ERROR, EvaConstant.ERROR_MSG_FILE_ERROR)
            animProxyListener.onVideoComplete()
        }
    }

    override fun startPlay(assetManager: AssetManager, assetsPath: String) {
        try {
            val fileContainer = EvaAssetsEvaFileContainer(assetManager, assetsPath)
            startPlay(fileContainer)
        } catch (e: Throwable) {
            animProxyListener.onFailed(EvaConstant.REPORT_ERROR_TYPE_FILE_ERROR, EvaConstant.ERROR_MSG_FILE_ERROR)
            animProxyListener.onVideoComplete()
        }
    }


    override fun startPlay(evaFileContainer: IEvaFileContainer) {
        ui {
            if (visibility != View.VISIBLE) {
                ELog.e(TAG, "AnimView is GONE, can't play")
                return@ui
            }
            if (!playerEva.isRunning()) {
                lastEvaFile = evaFileContainer
                playerEva.startPlay(evaFileContainer)
            } else {
                ELog.e(TAG, "is running can not start")
            }
        }
    }


    override fun stopPlay() {
        playerEva.stopPlay()
    }

    override fun isRunning(): Boolean {
        return playerEva.isRunning()
    }

    override fun getRealSize(): Pair<Int, Int> {
        return scaleTypeUtil.getRealSize()
    }

    private fun hide() {
        lastEvaFile?.close()
        ui {
            removeAllViews()
        }
    }

    private fun ui(f:()->Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) f() else uiHandler.post { f() }
    }

    /**
     * fix Error detachFromGLContext crash
     */
    private fun belowKitKat(): Boolean {
        return Build.VERSION.SDK_INT <= 19
    }

    private fun release() {
        try {
            surface?.release()
        } catch (error: Throwable) {
            ELog.e(TAG, "failed to release mSurfaceTexture= $surface: ${error.message}", error)
        }
        surface = null
    }
}