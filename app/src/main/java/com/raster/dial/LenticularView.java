package com.raster.dial;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * 多组光栅预览。按 view 宽高比选择竖/横素材，纹理 UV 做 aspect-fill 裁切。
 */
public class LenticularView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "LENTI";

    private static final String VS =
            "attribute vec2 aPos;\n" +
            "attribute vec2 aUV;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "  gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "  vUV = aUV;\n" +
            "}\n";

    private static final String FS =
            "precision mediump float;\n" +
            "uniform sampler2D uBase;\n" +
            "uniform sampler2D uReveal;\n" +
            "uniform vec4 uCropA;\n" +
            "uniform vec4 uCropB;\n" +
            "uniform float uFrac;\n" +
            "uniform vec2 uDir;\n" +
            "uniform float uSoft;\n" +
            "uniform float uParallax;\n" +
            "varying vec2 vUV;\n" +
            "void main() {\n" +
            "  vec2 d = uDir;\n" +
            "  float l = length(d);\n" +
            "  if (l < 0.001) { d = vec2(1.0, 0.0); } else { d = d / l; }\n" +
            "  vec2 uvA = clamp(vUV * uCropA.xy + uCropA.zw + d * uParallax, 0.0, 1.0);\n" +
            "  vec2 uvB = clamp(vUV * uCropB.xy + uCropB.zw - d * uParallax, 0.0, 1.0);\n" +
            "  vec3 ca = texture2D(uBase, uvA).rgb;\n" +
            "  vec3 cb = texture2D(uReveal, uvB).rgb;\n" +
            "  float extent = 0.5 * (abs(d.x) + abs(d.y));\n" +
            "  float u = dot(vUV - 0.5, d) / max(extent * 2.0, 0.0001) + 0.5;\n" +
            "  float edge = mix(-uSoft, 1.0 + uSoft, uFrac);\n" +
            "  float m = 1.0 - smoothstep(edge - uSoft, edge + uSoft, u);\n" +
            "  vec3 col = mix(ca, cb, m);\n" +
            "  float glow = exp(-pow((u - edge) / max(uSoft, 0.001), 2.0)) * 0.10;\n" +
            "  col += glow * vec3(0.18, 0.22, 0.35);\n" +
            "  float v = distance(vUV, vec2(0.5));\n" +
            "  col *= 1.0 - 0.28 * smoothstep(0.38, 0.95, v);\n" +
            "  gl_FragColor = vec4(col, 1.0);\n" +
            "}\n";

    private final FloatBuffer quad;
    private final String groupId;
    private int program;
    private int aPos, aUV, uBase, uReveal, uCropA, uCropB, uFrac, uDir, uSoft, uParallax;
    private int[] textures;
    private int[] texW, texH;
    private boolean landscape;
    private int viewW = 1, viewH = 1;

    private volatile float phase = 0f;
    private volatile float dirX = 1f, dirY = 0f;

    public LenticularView(Context context, String groupId) {
        super(context);
        this.groupId = groupId;
        setEGLContextClientVersion(2);
        setPreserveEGLContextOnPause(true);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);

        float[] v = {
                -1f, -1f, 0f, 1f,
                 1f, -1f, 1f, 1f,
                -1f,  1f, 0f, 0f,
                 1f,  1f, 1f, 0f
        };
        quad = ByteBuffer.allocateDirect(v.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quad.put(v).position(0);
    }

    public void setState(float phase, float dirX, float dirY) {
        this.phase = phase;
        this.dirX = dirX;
        this.dirY = dirY;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0f, 0f, 0f, 1f);
        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);

        program = buildProgram(VS, FS);
        aPos = GLES20.glGetAttribLocation(program, "aPos");
        aUV = GLES20.glGetAttribLocation(program, "aUV");
        uBase = GLES20.glGetUniformLocation(program, "uBase");
        uReveal = GLES20.glGetUniformLocation(program, "uReveal");
        uCropA = GLES20.glGetUniformLocation(program, "uCropA");
        uCropB = GLES20.glGetUniformLocation(program, "uCropB");
        uFrac = GLES20.glGetUniformLocation(program, "uFrac");
        uDir = GLES20.glGetUniformLocation(program, "uDir");
        uSoft = GLES20.glGetUniformLocation(program, "uSoft");
        uParallax = GLES20.glGetUniformLocation(program, "uParallax");
        textures = null;
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        viewW = width;
        viewH = height;
        GLES20.glViewport(0, 0, width, height);
        boolean land = width > height;
        if (land != landscape || textures == null) {
            landscape = land;
            loadGroup();
        }
        Log.i(TAG, "surface " + width + "x" + height + " landscape=" + landscape);
    }

    private void loadGroup() {
        GroupCatalog.Group g = GroupCatalog.find(getContext(), groupId);
        Bitmap[] bmps = GroupImages.load(getContext(), g, landscape);
        if (textures != null) {
            GLES20.glDeleteTextures(textures.length, textures, 0);
        }
        textures = new int[bmps.length];
        texW = new int[bmps.length];
        texH = new int[bmps.length];
        for (int i = 0; i < bmps.length; i++) {
            Bitmap b = bmps[i];
            if (b == null) {
                Log.e(TAG, "null bitmap " + i);
                continue;
            }
            textures[i] = loadTexture(b);
            texW[i] = b.getWidth();
            texH[i] = b.getHeight();
            b.recycle();
        }
        Log.i(TAG, "group=" + g.id + " count=" + bmps.length + " landscape=" + landscape);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        if (program == 0 || textures == null || textures.length == 0) {
            return;
        }

        int n = textures.length;
        float ph = phase;
        int seg = (int) Math.floor(ph) % n;
        if (seg < 0) {
            seg += n;
        }
        int next = (seg + 1) % n;
        float frac = ph - (float) Math.floor(ph);

        if (textures[seg] == 0 || textures[next] == 0) {
            return;
        }
        float[] ca = crop(texW[seg], texH[seg], viewW, viewH);
        float[] cb = crop(texW[next], texH[next], viewW, viewH);

        GLES20.glUseProgram(program);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[seg]);
        GLES20.glUniform1i(uBase, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textures[next]);
        GLES20.glUniform1i(uReveal, 1);

        GLES20.glUniform4f(uCropA, ca[0], ca[1], ca[2], ca[3]);
        GLES20.glUniform4f(uCropB, cb[0], cb[1], cb[2], cb[3]);
        GLES20.glUniform1f(uFrac, frac);
        GLES20.glUniform2f(uDir, dirX, dirY);
        GLES20.glUniform1f(uSoft, 0.09f);
        GLES20.glUniform1f(uParallax, 0.003f);

        quad.position(0);
        GLES20.glEnableVertexAttribArray(aPos);
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, quad);

        quad.position(2);
        GLES20.glEnableVertexAttribArray(aUV);
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, quad);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(aPos);
        GLES20.glDisableVertexAttribArray(aUV);
    }

    private float[] crop(int tw, int th, int vw, int vh) {
        if (tw <= 0 || th <= 0 || vw <= 0 || vh <= 0) {
            return new float[]{1f, 1f, 0f, 0f};
        }
        float target = (float) vw / vh;
        float src = (float) tw / th;
        if (src > target) {
            float sx = target / src;
            return new float[]{sx, 1f, (1f - sx) * 0.5f, 0f};
        } else {
            float sy = src / target;
            return new float[]{1f, sy, 0f, (1f - sy) * 0.5f};
        }
    }

    private int loadTexture(Bitmap bm) {
        int[] tex = new int[1];
        GLES20.glGenTextures(1, tex, 0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex[0]);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bm, 0);
        return tex[0];
    }

    private static int buildProgram(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "link: " + GLES20.glGetProgramInfoLog(p));
        }
        GLES20.glDeleteShader(v);
        GLES20.glDeleteShader(f);
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            Log.e(TAG, "compile: " + GLES20.glGetShaderInfoLog(s));
        }
        return s;
    }
}
