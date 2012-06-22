/*
   Copyright 2012 Harri Smatt

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

package fi.harism.viewbinder;

import java.nio.ByteBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;

/**
 * Actual View Binder implementation class.
 */
public class BinderView extends FrameLayout implements View.OnTouchListener {

	// Static values for current flip mode.
	private static final int FLIP_NEXT = 0;
	private static final int FLIP_NONE = 1;
	private static final int FLIP_PREV = 2;

	// Fragment Shader.
	private static final String SHADER_FRAGMENT = "\r\n"
			+ "precision mediump float;                             \r\n"
			+ "uniform sampler2D sTop;                              \r\n"
			+ "uniform sampler2D sBottom;                           \r\n"
			+ "uniform float uniformY;                              \r\n"
			+ "varying vec2 vPos;                                   \r\n"
			+ "void main() {                                        \r\n"
			+ "  if (vPos.y >= 0.0 && vPos.y > uniformY) {          \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vPos.y) * 0.5 + 0.5;   \r\n"
			+ "    gl_FragColor = texture2D(sTop, tPos);            \r\n"
			+ "    float c = max(0.0, uniformY);                    \r\n"
			+ "    gl_FragColor *= mix(1.0, 0.5, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y < 0.0 && vPos.y < uniformY) {      \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vPos.y) * 0.5 + 0.5;   \r\n"
			+ "    gl_FragColor = texture2D(sBottom, tPos);         \r\n"
			+ "    float c = min(1.0, 1.0 + uniformY);              \r\n"
			+ "    gl_FragColor *= mix(0.5, 1.0, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y >= 0.0) {                          \r\n"
			+ "    float vy = -vPos.y / uniformY;                   \r\n"
			+ "    vec2 tPos = vec2(vPos.x, vy);                    \r\n"
			+ "    tPos.x += (1.0 - uniformY) * 0.5 * vPos.x * vy;  \r\n"
			+ "    tPos = tPos * 0.5 + 0.5;                         \r\n"
			+ "    gl_FragColor = texture2D(sBottom, tPos);         \r\n"
			+ "    float c = max(0.0, uniformY);                    \r\n"
			+ "    gl_FragColor *= mix(0.5, 1.0, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "  else if (vPos.y < 0.0) {                           \r\n"
			+ "    float vy = -vPos.y / uniformY;                   \r\n"
			+ "    vec2 tPos = vec2(vPos.x, -vy);                   \r\n"
			+ "    tPos.x += (1.0 + uniformY) * 0.5 * vPos.x * vy;  \r\n"
			+ "    tPos = tPos * 0.5 + 0.5;                         \r\n"
			+ "    gl_FragColor = texture2D(sTop, tPos);            \r\n"
			+ "    float c = min(1.0, 1.0 + uniformY);              \r\n"
			+ "    gl_FragColor *= mix(1.0, 0.5, c);                \r\n"
			+ "  }                                                  \r\n"
			+ "}                                                    \r\n";

	// Vertex Shader.
	private static final String SHADER_VERTEX = "\r\n"
			+ "attribute vec2 aPos;                                 \r\n"
			+ "varying vec2 vPos;                                   \r\n"
			+ "void main() {                                        \r\n"
			+ "  gl_Position = vec4(aPos, 0.0, 1.0);                \r\n"
			+ "  vPos = aPos;                                       \r\n"
			+ "}                                                    \r\n";

	private int mFlipMode = FLIP_NONE;
	private Renderer mRenderer = new Renderer();
	private int mViewChildIndex = 0;
	private View[] mViewChildren = new View[0];
	private View mViewHook;
	private GLSurfaceView mViewRenderer;

	/**
	 * Default constructor.
	 */
	public BinderView(Context context) {
		super(context);
		init(context);
	}

	/**
	 * Default constructor.
	 */
	public BinderView(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context);
	}

	/**
	 * Default constructor.
	 */
	public BinderView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context);
	}

	/**
	 * Initializes renderer view for page transitions - plus hook view for
	 * grabbing touch events in front of actual view.
	 */
	private void init(Context context) {
		mViewRenderer = new GLSurfaceView(context);
		mViewRenderer.setEGLContextClientVersion(2);
		mViewRenderer.setRenderer(mRenderer);
		mViewRenderer.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		mViewHook = new View(context);
		mViewHook.setOnTouchListener(this);

		LayoutParams params = new LayoutParams(
				android.view.ViewGroup.LayoutParams.MATCH_PARENT,
				android.view.ViewGroup.LayoutParams.MATCH_PARENT);
		addView(mViewRenderer, params);
		addView(mViewHook, params);
	}

	@Override
	public boolean onTouch(View v, MotionEvent event) {
		if (mFlipMode == FLIP_NONE
				&& (mViewChildIndex >= mViewChildren.length || mViewChildren[mViewChildIndex]
						.onTouchEvent(event))) {
			return true;
		}

		float my = event.getY() * 2;
		switch (event.getAction()) {

		case MotionEvent.ACTION_DOWN:
			if (my > getHeight() && mViewChildIndex < mViewChildren.length - 1) {
				mFlipMode = FLIP_NEXT;
				updateRendererBitmaps();
				mViewRenderer.bringToFront();
				mRenderer.setFlipPosition(-1f);
				invalidate();
			}
			if (my < getHeight() && mViewChildIndex > 0) {
				mFlipMode = FLIP_PREV;
				updateRendererBitmaps();
				mViewRenderer.bringToFront();
				mRenderer.setFlipPosition(1f);
				invalidate();
			}
			break;

		case MotionEvent.ACTION_MOVE:
			if (mFlipMode != FLIP_NONE) {
				float fp = (getHeight() - my) / getHeight();
				fp = Math.min(1f, Math.max(-1f, fp));
				mRenderer.moveFlipPosition(fp);
			}
			break;

		case MotionEvent.ACTION_UP:
			if (my < getHeight() && mFlipMode == FLIP_NEXT) {
				mViewChildIndex++;
			}
			if (my > getHeight() && mFlipMode == FLIP_PREV) {
				mViewChildIndex--;
			}
			mFlipMode = FLIP_NONE;

			mRenderer.moveFlipPosition(my > getHeight() ? -1f : 1f);
			break;
		}

		return true;
	}

	/**
	 * Setter for View adapter for this Binder View.
	 * 
	 * @param adapter
	 *            View Adapter.
	 */
	public void setAdapter(BinderAdapter adapter) {
		int count = adapter.getCount();
		mViewChildren = new View[count];
		for (int i = 0; i < count; ++i) {
			mViewChildren[i] = adapter.createView(this, i);
		}
		mViewChildIndex = 0;
		setCurrentView(0);
	}

	/**
	 * Setter for current visible View.
	 */
	private void setCurrentView(int index) {
		if (index >= 0 && index < mViewChildren.length) {
			setViewVisibility(mViewChildren[index], View.VISIBLE);
			mViewChildren[index].bringToFront();
		}
		if (index > 0) {
			setViewVisibility(mViewChildren[index - 1], View.INVISIBLE);
		}
		if (index < mViewChildren.length - 1) {
			setViewVisibility(mViewChildren[index + 1], View.INVISIBLE);
		}

		for (int i = 0; i < index - 1; ++i) {
			removeView(mViewChildren[i]);
		}
		for (int i = index + 2; i < mViewChildren.length; ++i) {
			removeView(mViewChildren[i]);
		}

		invalidate();
	}

	/**
	 * Changes requested view visibility.
	 */
	private void setViewVisibility(View view, int visibility) {
		view.setVisibility(visibility);
		// View is already attached.
		if (indexOfChild(view) >= 0) {
			return;
		}
		// otherwise add view to ViewGroup.
		addView(view, 0);
	}

	/**
	 * Updates renderer Bitmaps.
	 */
	private void updateRendererBitmaps() {

		// Generate two offscreen bitmaps.
		Bitmap top = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);
		Bitmap bottom = Bitmap.createBitmap(getWidth(), getHeight(),
				Bitmap.Config.ARGB_8888);

		if (mFlipMode == FLIP_NEXT) {
			Canvas c = new Canvas(top);
			mViewChildren[mViewChildIndex].draw(c);
		}
		if (mFlipMode == FLIP_NEXT
				&& mViewChildIndex < mViewChildren.length - 1) {
			Canvas c = new Canvas(bottom);
			mViewChildren[mViewChildIndex + 1].draw(c);
		}

		if (mFlipMode == FLIP_PREV) {
			Canvas c = new Canvas(bottom);
			mViewChildren[mViewChildIndex].draw(c);
		}
		if (mFlipMode == FLIP_PREV && mViewChildIndex > 0) {
			Canvas c = new Canvas(top);
			mViewChildren[mViewChildIndex - 1].draw(c);
		}

		mRenderer.setBitmaps(top, bottom);
	}

	/**
	 * Private renderer class.
	 */
	private class Renderer implements GLSurfaceView.Renderer {

		private Bitmap mBitmapTop, mBitmapBottom;
		private ByteBuffer mCoords;
		private float mFlipPosition;
		private float mFlipPositionTarget;
		private long mLastRenderTime;
		private int mProgram;
		private int[] mTextureIds;

		/**
		 * Default constructor.
		 */
		public Renderer() {
			final byte[] COORDS = { -1, 1, -1, -1, 1, 1, 1, -1 };
			mCoords = ByteBuffer.allocateDirect(8);
			mCoords.put(COORDS).position(0);
		}

		/**
		 * Private shader loader.
		 */
		private final int loadProgram(String vs, String fs) throws Exception {
			int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vs);
			int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fs);
			int program = GLES20.glCreateProgram();
			if (program != 0) {
				GLES20.glAttachShader(program, vertexShader);
				GLES20.glAttachShader(program, fragmentShader);
				GLES20.glLinkProgram(program);
				int[] linkStatus = new int[1];
				GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS,
						linkStatus, 0);
				if (linkStatus[0] != GLES20.GL_TRUE) {
					String error = GLES20.glGetProgramInfoLog(program);
					GLES20.glDeleteProgram(program);
					throw new Exception(error);
				}
			}
			return program;
		}

		/**
		 * Private shader loader/compiler.
		 */
		private final int loadShader(int shaderType, String source)
				throws Exception {
			int shader = GLES20.glCreateShader(shaderType);
			if (shader != 0) {
				GLES20.glShaderSource(shader, source);
				GLES20.glCompileShader(shader);
				int[] compiled = new int[1];
				GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS,
						compiled, 0);
				if (compiled[0] == 0) {
					String error = GLES20.glGetShaderInfoLog(shader);
					GLES20.glDeleteShader(shader);
					throw new Exception(error);
				}
			}
			return shader;
		}

		/**
		 * Animates flip position to requested position.
		 */
		public void moveFlipPosition(float posY) {
			mFlipPositionTarget = posY;
			mViewRenderer.requestRender();
		}

		@Override
		public void onDrawFrame(GL10 unused) {

			// Disable unneeded rendering flags.
			GLES20.glDisable(GLES20.GL_DEPTH_TEST);
			GLES20.glDisable(GLES20.GL_CULL_FACE);

			// Allocate new texture ids if needed.
			if (mTextureIds == null) {
				mTextureIds = new int[2];
				GLES20.glGenTextures(2, mTextureIds, 0);
				for (int textureId : mTextureIds) {
					// Set texture attributes.
					GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
					GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
							GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
				}
			}

			// If we have new Bitmaps.
			if (mBitmapTop != null) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmapTop, 0);
				mBitmapTop.recycle();
				mBitmapTop = null;
			}
			if (mBitmapBottom != null) {
				GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
				GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, mBitmapBottom, 0);
				mBitmapBottom.recycle();
				mBitmapBottom = null;
			}

			// Use our vertex/fragment shader program.
			GLES20.glUseProgram(mProgram);
			// Fetch variable ids.
			int uniformY = GLES20.glGetUniformLocation(mProgram, "uniformY");
			int sTop = GLES20.glGetUniformLocation(mProgram, "sTop");
			int sBottom = GLES20.glGetUniformLocation(mProgram, "sBottom");
			int aPos = GLES20.glGetAttribLocation(mProgram, "aPos");

			// If there's room for animation.
			if (Math.abs(mFlipPosition - mFlipPositionTarget) > 0.01f) {
				long currentTime = SystemClock.uptimeMillis();
				float t = Math.min(1f, (currentTime - mLastRenderTime) * .01f);
				mFlipPosition = mFlipPosition
						+ (mFlipPositionTarget - mFlipPosition) * t;
				mLastRenderTime = currentTime;
				mViewRenderer.requestRender();
			}
			// If we're done with animation plus user left us with touch up
			// event.
			else if (mFlipMode == FLIP_NONE) {
				post(new Runnable() {
					@Override
					public void run() {
						setCurrentView(mViewChildIndex);
					}
				});
			}

			// Set flip position variable.
			GLES20.glUniform1f(uniformY, mFlipPosition);
			// Set texture variables.
			GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[0]);
			GLES20.glUniform1i(sTop, 0);
			GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
			GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureIds[1]);
			GLES20.glUniform1i(sBottom, 1);
			// Set vertex position variables.
			GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_BYTE, false, 0,
					mCoords);
			GLES20.glEnableVertexAttribArray(aPos);
			// Render quad.
			GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
		}

		@Override
		public void onSurfaceChanged(GL10 unused, int width, int height) {
			// All we have to do is set viewport.
			GLES20.glViewport(0, 0, width, height);
		}

		@Override
		public void onSurfaceCreated(GL10 unused, EGLConfig config) {
			try {
				// Force instantiation for new texture ids.
				mTextureIds = null;
				// Load vertex/fragment shader program.
				mProgram = loadProgram(SHADER_VERTEX, SHADER_FRAGMENT);
			} catch (final Exception ex) {
				// On error show Toast.
				post(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(getContext(), ex.toString(),
								Toast.LENGTH_SHORT).show();
					}
				});
			}
		}

		/**
		 * Setter for Bitmaps.
		 */
		public void setBitmaps(Bitmap top, Bitmap bottom) {
			if (top != null) {
				mBitmapTop = top;
			}
			if (bottom != null) {
				mBitmapBottom = bottom;
			}
		}

		/**
		 * Setter for flip position, value between [-1, 1].
		 */
		public void setFlipPosition(float posY) {
			mFlipPosition = posY;
			mFlipPositionTarget = posY;
			mLastRenderTime = SystemClock.uptimeMillis();
			mViewRenderer.requestRender();
		}

	}

}
