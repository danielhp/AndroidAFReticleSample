package com.example.android.camera2basic;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

import com.example.android.camera2basic.gl.EglCore;
import com.example.android.camera2basic.gl.GLUtil;
import com.example.android.camera2basic.gl.WindowSurface;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/** *
 * Base camera rendering class. Responsible for rendering to proper window contexts, as well as
 * recording video with built-in media recorder.
 *
 * Subclass this and add any kind of fun stuff u want, new shaders, textures, uniforms - go to town!
 *
 * TODO: add methods for users to create their own mediarecorders/change basic settings of default mr
 */

public class CameraRenderer extends Thread implements SurfaceTexture.OnFrameAvailableListener
{
    private static final String TAG = CameraRenderer.class.getSimpleName();
    private static final String THREAD_NAME = "CameraRendererThread";

    /**
     * Current context for use with utility methods
     */
    protected Context mContext;

    protected int mSurfaceWidth, mSurfaceHeight;

    protected float mSurfaceAspectRatio;

    /**
     * main texture for display, based on TextureView that is created in activity or fragment
     * and passed in after onSurfaceTextureAvailable is called, guaranteeing its existence.
     */
    private SurfaceTexture mSurfaceTexture;

    /**
     * EGLCore used for creating {@link WindowSurface}s for preview and recording
     */
    private EglCore mEglCore;

    /**
     * Primary {@link WindowSurface} for rendering to screen
     */
    private WindowSurface mWindowSurface;

    /**
     * Texture created for GLES rendering of camera data
     */
    private SurfaceTexture mPreviewTexture;

    private final String vertexShaderCode = "#version 300 es\n" +
            "uniform mat4 camTextureTransform;\n" +
            "in vec4 position;\n" +
            "in vec4 inputTextureCoordinate;\n" +
            "out vec2 textureCoordinate;\n" +
            "void main() {\n" +
            "    //camera texcoord needs to be manipulated by the transform given back from the system\n" +
            "    textureCoordinate = (camTextureTransform * inputTextureCoordinate).xy;\n" +
            "    gl_Position = position;\n" +
            "}";

    private final String fragmentShaderCode = "#version 300 es\n" +
            "#extension GL_OES_EGL_image_external_essl3 : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES camTexture;\n" +
            "in vec2 textureCoordinate;\n" +
            "out vec4 fragColor;\n" +
            "void main () {\n" +
            "    vec4 cameraColor = texture(camTexture, textureCoordinate);\n" +
            "    fragColor = cameraColor;\n" +
            "}";


    /**
     * Basic mesh rendering code
     */
    private static float squareSize = 1.0f;

    private static float squareCoords[] = {
            -squareSize, squareSize, // 0.0f,     // top left
            squareSize, squareSize, // 0.0f,   // top right
            -squareSize, -squareSize, // 0.0f,   // bottom left
            squareSize, -squareSize, // 0.0f,   // bottom right
    };

    private static short drawOrder[] = {0, 1, 2, 1, 3, 2};

    private FloatBuffer textureBuffer;

    private float textureCoords[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    protected int mGLProgId;

    private FloatBuffer vertexBuffer;

    private ShortBuffer drawListBuffer;


    /**
     * matrix for transforming our camera texture, available immediately after {@link #mPreviewTexture}s
     * {@code updateTexImage()} is called in our main {@link #draw()} loop.
     */
    private float[] mCameraTransformMatrix = new float[16];

    /**
     * Handler for communcation with the UI thread. Implementation below at
     * {@link CameraRenderer.RenderHandler RenderHandler}
     */
    private RenderHandler mHandler;

    /**
     * Interface listener for some callbacks to the UI thread when rendering is setup and finished.
     */
    private OnRendererReadyListener mOnRendererReadyListener;

    /**
     * Width and height storage of our viewport size, so we can properly accomodate any size View
     * used to display our preview on screen.
     */
    private int mViewportWidth, mViewportHeight;

    /**
     * Reference to our users CameraFragment to ease setting viewport size. Thought about decoupling but wasn't
     * worth the listener/callback hastle
     */
    private CameraFragment mCameraFragment;


    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    private int positionLocation = -1;
    private int textureCoordinateLocation = -1;
    private int camTextureLocation = -1;
    private int camTextureTransformLocation = -1;

    private int mCameraTextureID = -1;

    /**
     * Simple ctor to use default shaders
     */
    public CameraRenderer(Context context, SurfaceTexture texture, int width, int height) {

        this.setName(THREAD_NAME);

        this.mContext = context;
        this.mSurfaceTexture = texture;

        this.mSurfaceWidth = width;
        this.mSurfaceHeight = height;
        this.mSurfaceAspectRatio = (float)width / height;

    }



    private void initialize() {

        setupCameraFragment();
        setViewport(mSurfaceWidth, mSurfaceHeight);
    }

    private void setupCameraFragment() {
        if(mCameraFragment == null) {
            throw new RuntimeException("CameraFragment is null! Please call setCameraFragment prior to initialization.");
        }
    }


    /**
     * Initialize all necessary components for GLES rendering, creating window surfaces for drawing
     * the preview as well as the surface that will be used by MediaRecorder for recording
     */
    public void initGL() {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);

        //create preview surface
        mWindowSurface = new WindowSurface(mEglCore, mSurfaceTexture);
        mWindowSurface.makeCurrent();


        mPreviewTexture = new SurfaceTexture(mCameraTextureID);
        mPreviewTexture.setOnFrameAvailableListener(this);

        initGLComponents();
    }

    ByteBuffer lutBuffer;
    int cubeSize = 16;

    protected void initGLComponents() {

        mGLProgId = GLUtil.loadProgram(vertexShaderCode, fragmentShaderCode);
        GLUtil.checkGlError("loadProgram");

        drawListBuffer = GLUtil.createShortBuffer(drawOrder);
        vertexBuffer = GLUtil.createFloatBuffer(squareCoords);
        textureBuffer = GLUtil.createFloatBuffer(textureCoords);


        camTextureLocation = GLES30.glGetUniformLocation(mGLProgId, "camTexture");
        GLUtil.checkGlError("Texture bind");
        camTextureTransformLocation = GLES30.glGetUniformLocation(mGLProgId, "camTextureTransform");
        textureCoordinateLocation = GLES30.glGetAttribLocation(mGLProgId, "inputTextureCoordinate");
        positionLocation = GLES30.glGetAttribLocation(mGLProgId, "position");
        GLUtil.checkGlError("Texture bind");

        //camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureID);
        GLUtil.checkGlError("Texture bind");

        mOnRendererReadyListener.onRendererReady();
    }


    // ------------------------------------------------------------
    // deinit
    // ------------------------------------------------------------

    public void deinitGL() {
        deinitGLComponents();

        mWindowSurface.release();

        mEglCore.release();
    }

    protected void deinitGLComponents() {
        GLES30.glDeleteProgram(mGLProgId);

        mPreviewTexture.release();
        mPreviewTexture.setOnFrameAvailableListener(null);
    }

    // ------------------------------------------------------------
    // setup
    // ------------------------------------------------------------


    @Override
    public synchronized void start() {
        initialize();

        if(mOnRendererReadyListener == null) throw new RuntimeException("OnRenderReadyListener is not set! Set listener prior to calling start()");

        super.start();
    }


    /**
     * primary loop - this does all the good things
     */
    @Override
    public void run() {
        Looper.prepare();

        //create handler for communication from UI
        mHandler = new RenderHandler(this);

        //initialize all GL on this context
        initGL();

        Looper.loop();

        //we're done here
        deinitGL();

        mOnRendererReadyListener.onRendererFinished();
    }

    /**
     * stop our thread, and make sure we kill a recording if its still happening
     *
     * this should only be called from our handler to ensure thread-safe
     */
    public void shutdown() {

        //kill ouy thread
        Looper.myLooper().quit();
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        boolean swapResult;

        synchronized (this) {
            updatePreviewTexture();

            draw();


            mWindowSurface.makeCurrent();
            swapResult = mWindowSurface.swapBuffers();


            if (!swapResult) {
                // This can happen if the Activity stops without waiting for us to halt.
                Log.e(TAG, "swapBuffers failed, killing renderer thread");
                shutdown();
            }
        }
    }

    /**
     * main draw routine
     */
    public void draw() {
        GLES30.glViewport(0, 0, mViewportWidth, mViewportHeight);

        GLES30.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        //set shader
        GLES30.glUseProgram(mGLProgId);

        setUniformsAndAttributes();

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, drawOrder.length, GLES30.GL_UNSIGNED_SHORT, drawListBuffer);

        // draw cleanup
        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(textureCoordinateLocation);

        // GL is a state machine and some say that it's better to unbind the texture.
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    /**
     * update the SurfaceTexture to the latest camera image
     */
    protected void updatePreviewTexture() {
        mPreviewTexture.updateTexImage();
        mPreviewTexture.getTransformMatrix(mCameraTransformMatrix);
    }

    /**
     * base amount of attributes needed for rendering camera to screen
     */
    protected void setUniformsAndAttributes(){

        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT, false, 4 * 2, vertexBuffer);

        //camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureID);
        GLES30.glUniform1i(camTextureLocation, 1);

        GLES30.glEnableVertexAttribArray(textureCoordinateLocation);
        GLES30.glVertexAttribPointer(textureCoordinateLocation, 2, GLES30.GL_FLOAT, false, 4 * 2, textureBuffer);

        GLES30.glUniformMatrix4fv(camTextureTransformLocation, 1, false, mCameraTransformMatrix, 0);
    }


    //getters and setters

    public void setViewport(int viewportWidth, int viewportHeight)
    {
        mViewportWidth = viewportWidth;
        mViewportHeight = viewportHeight;
    }

    public SurfaceTexture getPreviewTexture() {
        return mPreviewTexture;
    }

    public RenderHandler getRenderHandler() {
        return mHandler;
    }

    public void setOnRendererReadyListener(OnRendererReadyListener listener) {
        mOnRendererReadyListener = listener;

    }


    public void setCameraFragment(CameraFragment cameraFragment) {
        mCameraFragment = cameraFragment;
    }


    public static class RenderHandler extends Handler {
        private static final String TAG = RenderHandler.class.getSimpleName();

        private static final int MSG_SHUTDOWN = 0;

        /**
         * Our camera renderer ref, weak since we're dealing with static class so it doesn't leak
         */
        private WeakReference<CameraRenderer> mWeakRenderer;

        /**
         * Call from render thread.
         */
        public RenderHandler(CameraRenderer rt) {
            mWeakRenderer = new WeakReference<>(rt);
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(RenderHandler.MSG_SHUTDOWN));
        }

        @Override
        public void handleMessage(Message msg)
        {
            CameraRenderer renderer = mWeakRenderer.get();
            if (renderer == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            int what = msg.what;
            switch (what) {
                case MSG_SHUTDOWN:
                    renderer.shutdown();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    /**
     * Interface for callbacks when render thread completes its setup
     */
    public interface OnRendererReadyListener {

        void onRendererReady();

        /**
         * Called once the looper is killed and our {@link #run()} method completes
         */
        void onRendererFinished();
    }
}
