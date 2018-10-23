/*
 * Copyright 2014 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.AFSample;

import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.opengl.Matrix;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Some OpenGL utility functions. From Grafika
 */
public class GLUtil {
    public static final String TAG = "GlUtil";

    /** Identity matrix for general use.  Don't modify or life will get weird. */
    public static final float[] IDENTITY_MATRIX;
    static {
        IDENTITY_MATRIX = new float[16];
        Matrix.setIdentityM(IDENTITY_MATRIX, 0);
    }

    private static final int SIZEOF_FLOAT = 4;


    private GLUtil() {}     // do not instantiate

    /**
     * Creates a new program from the supplied vertex and fragment shaders.
     *
     * @return A handle to the program, or 0 on failure.
     */
    public static int loadProgram(String vertexSource, String fragmentSource) {

        int vertexShader = loadShader(GLES30.GL_VERTEX_SHADER, vertexSource);
        if (vertexShader == 0) return 0;

        int pixelShader = loadShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource);
        if (pixelShader == 0) return 0;

        int program = GLES30.glCreateProgram();
        checkGlError("glCreateProgram");
//        if (program == 0) Log.e(TAG, "Could not create program");

        GLES30.glAttachShader(program, vertexShader);

        checkGlError("glAttachShader");
        GLES30.glAttachShader(program, pixelShader);

        checkGlError("glAttachShader");
        GLES30.glLinkProgram(program);
        checkGlError("glLinkProgram");

        int[] linkStatus = new int[1];
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0);
        checkGlError("glGetProgramiv");

        if (linkStatus[0] != GLES30.GL_TRUE) {
            Log.e(TAG, "Could not link program: ");
            Log.e(TAG, GLES30.glGetProgramInfoLog(program));
            GLES30.glDeleteProgram(program);
            program = 0;
        }

        return program;
    }

    /**
     * Compiles the provided shader source.
     *
     * @return A handle to the shader, or 0 on failure.
     */
    public static int loadShader(int shaderType, String source) {
        int shader = GLES30.glCreateShader(shaderType);
        checkGlError("glCreateShader type=" + shaderType);
        GLES30.glShaderSource(shader, source);
        GLES30.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + shaderType + ":");
            Log.e(TAG, " " + GLES30.glGetShaderInfoLog(shader));
            GLES30.glDeleteShader(shader);
            shader = 0;
        }
        return shader;
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    public static void checkGlError(String op) {
        int error = GLES30.glGetError();
        if (error != GLES30.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }



    /**
     * Allocates a direct float buffer, and populates it with the float array data.
     */
    public static FloatBuffer createFloatBuffer(float[] coords) {
        // Allocate a direct ByteBuffer, using 4 bytes per float, and copy coords into it.
        ByteBuffer bb = ByteBuffer.allocateDirect(coords.length * SIZEOF_FLOAT);
        bb.order(ByteOrder.nativeOrder());
        FloatBuffer fb = bb.asFloatBuffer();
        fb.put(coords);
        fb.position(0);
        return fb;
    }


    public static ShortBuffer createShortBuffer(short[] coords) {

        ByteBuffer ibb = ByteBuffer.allocateDirect(coords.length * 2);
        ibb.order(ByteOrder.nativeOrder());
        ShortBuffer sb = ibb.asShortBuffer();
        sb.put(coords);
        sb.position(0);
        return sb;
    }

    /**
     * Creates a texture object suitable for use with drawFrame().
     */
    public static int createExternal2DTexture() {
        int[] textures = new int[1];
        GLES30.glGenTextures(1, textures, 0);

        int texId = textures[0];
        GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texId);

        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
        GLES30.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST); //GL_LINEAR?
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);

        return texId;
    }
}
