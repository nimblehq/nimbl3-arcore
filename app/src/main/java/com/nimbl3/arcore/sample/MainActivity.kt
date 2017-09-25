/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.nimbl3.arcore.sample

import android.opengl.GLSurfaceView
import android.os.Bundle
import android.support.design.widget.Snackbar
import android.support.v7.app.AppCompatActivity
import android.view.View
import android.view.WindowManager
import android.widget.RadioButton
import android.widget.Toast
import com.google.ar.core.*
import com.nimbl3.arcore.sample.helper.CameraPermissionHelper
import com.nimbl3.arcore.sample.main.Mode
import com.nimbl3.arcore.sample.main.OurSurfaceRenderer


class MainActivity : AppCompatActivity(), OurSurfaceRenderer.OurSurfaceRendererCallback {

    companion object {
        const val TAG = "MainActivity"
    }

    lateinit private var surfaceView: GLSurfaceView
    lateinit private var ourSurfaceRenderer: OurSurfaceRenderer

    lateinit var defaultConfig: Config
    lateinit var session: Session

    private var loadingMessageSnackbar: Snackbar? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (!setupSession()) return
        setupSurfaceView()
    }

    private fun setupSession(): Boolean {
        session = Session(this)

        defaultConfig = Config.createDefaultConfig()
        if (!session.isSupported(defaultConfig)) {
            Toast.makeText(this, getString(R.string.ar_device_support), Toast.LENGTH_LONG).show()
            finish()
            return false
        }

        return true
    }

    private fun setupSurfaceView() {
        surfaceView = findViewById(R.id.surfaceView)

        // Set up renderer.
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0) // Alpha used for plane blending.
        ourSurfaceRenderer = OurSurfaceRenderer(this, session, surfaceView, this)
        ourSurfaceRenderer.setup()
        surfaceView.setRenderer(ourSurfaceRenderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
    }

    override fun onResume() {
        super.onResume()
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            showLoadingMessage(true)
            // Note that order matters - see the note in onPause(), the reverse applies here.
            session.resume(defaultConfig)
            surfaceView.onResume()
        } else {
            CameraPermissionHelper.requestCameraPermission(this)
        }
    }

    override fun onPlanesDetected() {
        loadingMessageSnackbar?.let {
            this.showLoadingMessage(false)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this, getString(R.string.camera_permission_toast), Toast.LENGTH_LONG).show()
            finish()
        }
    }

    fun onRadioButtonClicked(view: View) {
        val radioButton = view as RadioButton
        when (radioButton.id) {
            R.id.radioViking -> ourSurfaceRenderer.onModeChange(Mode.VIKING)
            R.id.radioCannon -> ourSurfaceRenderer.onModeChange(Mode.CANNON)
            R.id.radioTarget -> ourSurfaceRenderer.onModeChange(Mode.TARGET)
        }
    }

    private fun showLoadingMessage(isShow: Boolean) {
        runOnUiThread {
            if (isShow) {
                loadingMessageSnackbar = Snackbar.make(findViewById(android.R.id.content), getString(R.string.searching_for_surfaces), Snackbar.LENGTH_INDEFINITE)
                loadingMessageSnackbar?.view?.setBackgroundColor(0xbf323232.toInt())
                loadingMessageSnackbar?.show()
            } else {
                loadingMessageSnackbar?.dismiss()
                loadingMessageSnackbar = null
            }
        }
    }
}
