package com.example.android.camera2basic;

import android.graphics.SurfaceTexture;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.support.annotation.NonNull;

import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PreviewRenderer implements GLSurfaceView.Renderer {

//    private static final String TAG = PreviewRenderer.class.getSimpleName();

    /**
     * Texture created for GLES rendering of camera data
     */
    private SurfaceTexture mPreviewTexture;
    private SurfaceTexture.OnFrameAvailableListener frameAvailableListener;

    /**
     * Width and height storage of our viewport size, so we can properly accomodate any size View
     * used to display our preview on screen.
     */
    private int mViewportWidth, mViewportHeight;

    private int positionLocation = -1;
    private int textureCoordinateLocation = -1;
    private int camTextureLocation = -1;
    private int camTextureTransformLocation = -1;
    private int mCameraTextureID = -1;

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

    private int mGLProgId;

    private FloatBuffer vertexBuffer;

    private ShortBuffer drawListBuffer;

    /**
     * matrix for transforming our camera texture, available immediately after {@link #mPreviewTexture}s
     * {@code updateTexImage()} is called in our main {@link #draw()} loop.
     */
    private float[] mCameraTransformMatrix = new float[16];

    private SurfaceListener surfaceListener;

    private int orientation = 270;
    private boolean verticalFlip = true;
    private boolean rendering = true;

    interface SurfaceListener{
        void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture);
    }

    PreviewRenderer(@NonNull SurfaceTexture.OnFrameAvailableListener frameAvailableListener, int width, int height, @NonNull SurfaceListener surfaceListener) {
        this.frameAvailableListener = frameAvailableListener;
        mViewportWidth = width;
        mViewportHeight = height;
        this.surfaceListener = surfaceListener;
    }

    public void setOrientation(int orientation) { this.orientation = orientation; }

    public void setVerticalFlip(boolean verticalFlip) { this.verticalFlip = verticalFlip; }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
//        Log.i(TAG, "onSurfaceCreated: " + mViewportWidth + "x" + mViewportHeight);
        mCameraTextureID = GLUtil.createExternal2DTexture();
        mPreviewTexture = new SurfaceTexture(mCameraTextureID);
//        mPreviewTexture.setDefaultBufferSize(mViewportWidth, mViewportHeight);
        mPreviewTexture.setOnFrameAvailableListener(frameAvailableListener);

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

//        surfaceListener.onSurfaceTextureAvailable(mPreviewTexture);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
//        Log.i(TAG, "onSurfaceChanged: " + width + "x" + height);
        mViewportWidth = width;
        mViewportHeight = height;
        mPreviewTexture.setDefaultBufferSize(mViewportWidth, mViewportHeight);
        surfaceListener.onSurfaceTextureAvailable(mPreviewTexture);

    }

    @Override
    public void onDrawFrame(GL10 gl) {
//        Log.i(TAG, "onDrawFrame: ");
        if(!rendering) return;
        mPreviewTexture.updateTexImage();
        mPreviewTexture.getTransformMatrix(mCameraTransformMatrix);
        if (orientation == 90 || orientation == 270) {
            Matrix.translateM(mCameraTransformMatrix, 0, 0f, 1f, 0);
            Matrix.rotateM(mCameraTransformMatrix, 0, orientation, 0, 0, 1f);
        }
        if (verticalFlip) {
            Matrix.translateM(mCameraTransformMatrix, 0, 1f, 1f, 0);
            Matrix.scaleM(mCameraTransformMatrix, 0, -1f, -1f, 1f);
        }
        draw();
    }

    /**
     * main draw routine
     */
    private void draw() {
        GLES30.glViewport(0, 0, mViewportWidth, mViewportHeight);

        GLES30.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);

        //set shader
        GLES30.glUseProgram(mGLProgId);

        GLES30.glEnableVertexAttribArray(positionLocation);
        GLES30.glVertexAttribPointer(positionLocation, 2, GLES30.GL_FLOAT, false, 4 * 2, vertexBuffer);

        //camera texture
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1);
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mCameraTextureID);
        GLES30.glUniform1i(camTextureLocation, 1);

        GLES30.glEnableVertexAttribArray(textureCoordinateLocation);
        GLES30.glVertexAttribPointer(textureCoordinateLocation, 2, GLES30.GL_FLOAT, false, 4 * 2, textureBuffer);

        GLES30.glUniformMatrix4fv(camTextureTransformLocation, 1, false, mCameraTransformMatrix, 0);

        GLES30.glDrawElements(GLES30.GL_TRIANGLES, drawOrder.length, GLES30.GL_UNSIGNED_SHORT, drawListBuffer);

        // draw cleanup
        GLES30.glDisableVertexAttribArray(positionLocation);
        GLES30.glDisableVertexAttribArray(textureCoordinateLocation);

        // GL is a state machine and some say that it's better to unbind the texture.
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    public void stopRendering(){
        rendering = false;
    }
}
