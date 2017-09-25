package com.nimbl3.arcore.sample.main

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.support.v4.app.FragmentActivity
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.Toast
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.PlaneHitResult
import com.google.ar.core.Session
import com.nimbl3.arcore.sample.MainActivity
import com.nimbl3.arcore.sample.google.*
import java.io.IOException
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.collections.HashMap

class OurSurfaceRenderer(val context: FragmentActivity,
                         val session: Session,
                         val glSurfaceView : GLSurfaceView,
                         val callBack: OurSurfaceRendererCallback) : GLSurfaceView.Renderer {

    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val pointCloud = PointCloudRenderer()

    private val vikingObject = ObjectRenderer()
    private val cannonObject = ObjectRenderer()
    private val targetObject = ObjectRenderer()

    private var vikingAttachment: PlaneAttachment? = null
    private var cannonAttachment: PlaneAttachment? = null
    private var targetAttachment: PlaneAttachment? = null

    lateinit private var gestureDetector: GestureDetector

    var isDragging: Boolean = false

    var mode: Mode = Mode.VIKING // default

    // Tap handling and UI.
    private val queuedSingleTaps = ArrayBlockingQueue<MotionEvent>(1000000)
    private val queuedDoubleTaps = ArrayBlockingQueue<MotionEvent>(100)

    // Temporary matrix allocated here o reduce number of allocations for each frame.
    private val anchorMatrix = FloatArray(16)

    interface OurSurfaceRendererCallback {
        fun onPlanesDetected()
    }

    fun setup() {
        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                onStopRecordingDrag()
                return true
            }

            override fun onDown(e: MotionEvent?): Boolean {
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent?, distanceX: Float, distanceY: Float): Boolean {
                onStartRecordingDrag(e2)
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                queuedDoubleTaps.offer(e)
                return true;
            }
        })
        glSurfaceView.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }
    }

    fun onModeChange(mode: Mode) {
        this.mode = mode
    }

    fun onStartRecordingDrag(event: MotionEvent?) {
        isDragging = true
        queuedSingleTaps.offer(event)
    }

    fun onStopRecordingDrag() {
        isDragging = false
    }

    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        Log.e(MainActivity.TAG, "onSurfaceCreated")

        // Create the texture and pass it to ARCore session to be filled during update().
        backgroundRenderer.createOnGlThread(context)
        session.setCameraTextureName(backgroundRenderer.textureId)

        // Prepare our rendering objects.
        renderOurObjects()

        // prepare the planar surface nest
        try {
            planeRenderer.createOnGlThread(context, "trigrid.png")
        } catch (e: IOException) {
            Log.e(MainActivity.TAG, "Failed to read plane texture")
        }

        pointCloud.createOnGlThread(context)
    }

    fun renderOurObjects() {
        shownMap = HashMap()
        try {
            vikingObject.createOnGlThread(context, "viking.obj", "viking.png")
            setSameMaterial(vikingObject)
            cannonObject.createOnGlThread(context, "cannon.obj", "cannon.png")
            setSameMaterial(cannonObject)
            targetObject.createOnGlThread(context, "target.obj", "target.png")
            setSameMaterial(targetObject)
            setShown(Mode.VIKING, false)
            setShown(Mode.CANNON, false)
            setShown(Mode.TARGET, false)
        } catch (e: IOException) {
            Log.e(MainActivity.TAG, "Failed to read obj file")
        }
    }

    override fun onDrawFrame(gl: GL10) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            val frame = session.update()
            handleDoubleTaps(frame)
            handleDrag(frame)
            drawBackground(frame)

            // If not tracking, don't draw 3d objects.
            if (!checkTrackingState(frame)) return

            val projectionMatrix = computeProjectionMatrix()
            val viewMatrix = computeViewMatrix(frame)
            val lightIntensity = frame.lightEstimate.pixelIntensity

            visualizeTrackedPoints(frame, projectionMatrix, viewMatrix)
            checkPlaneDetected()

            // Draw the visible planes - the one we are aiming to interact with
            visualizePlanes(frame, projectionMatrix)

            // Draw the objects
            drawObject(vikingObject, vikingAttachment, Mode.VIKING.scaleFactor, projectionMatrix, viewMatrix, lightIntensity)
            drawObject(cannonObject, cannonAttachment, Mode.CANNON.scaleFactor, projectionMatrix, viewMatrix, lightIntensity)
            drawObject(targetObject, targetAttachment, Mode.TARGET.scaleFactor, projectionMatrix, viewMatrix, lightIntensity)

        } catch (t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(MainActivity.TAG, "Exception on the OpenGL thread", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.e(MainActivity.TAG, "onSurfaceChanged")
        GLES20.glViewport(0, 0, width, height)
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        session.setDisplayGeometry(width.toFloat(), height.toFloat())
    }

    private fun setSameMaterial(objectRenderer: ObjectRenderer) {
        objectRenderer.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f)
    }

    private fun drawBackground(frame: Frame?) {
        backgroundRenderer.draw(frame)
    }

    private fun computeProjectionMatrix(): FloatArray {
        val projectionMatrix = FloatArray(16)
        session.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
        return projectionMatrix
    }

    private fun computeViewMatrix(frame: Frame): FloatArray {
        val viewMatrix = FloatArray(16)
        frame.getViewMatrix(viewMatrix, 0)
        return viewMatrix
    }

    fun alreadyShown(mode: Mode): Boolean {
        return shownMap.get(mode)!!
    }

    lateinit var shownMap: HashMap<Mode, Boolean>
    fun setShown(mode: Mode, shown: Boolean) {
        shownMap.set(mode, shown)
    }

    fun handleDoubleTaps(frame: Frame) {
        val tapMotionEvent = queuedDoubleTaps.poll()
        if (tapMotionEvent != null && frame.trackingState == Frame.TrackingState.TRACKING) {
            for (hit in frame.hitTest(tapMotionEvent)) {
                // Check if any plane was hit, and if it was hit inside the plane polygon.
                if (hit is PlaneHitResult && hit.isHitInPolygon && !alreadyShown(mode)) {
                    when (mode) {
                        Mode.VIKING -> vikingAttachment = addSessionAnchorFromAttachment(vikingAttachment, hit)
                        Mode.CANNON -> cannonAttachment = addSessionAnchorFromAttachment(cannonAttachment, hit)
                        Mode.TARGET -> targetAttachment = addSessionAnchorFromAttachment(targetAttachment, hit)
                    }
                    // Hits are sorted by depth. Consider only closest hit on a plane.
                    setShown(mode, true)
                    break
                } else {
                    when (mode) {
                        Mode.VIKING -> vikingAttachment = null
                        Mode.CANNON -> cannonAttachment = null
                        Mode.TARGET -> targetAttachment = null
                    }
                    setShown(mode, false)
                    break;
                }
            }
        }
    }

    fun handleDrag(frame: Frame) {
        val tapMotionEvent = queuedSingleTaps.poll()
        if (tapMotionEvent != null && isDragging
                && frame.trackingState == Frame.TrackingState.TRACKING) {
            for (hit in frame.hitTest(tapMotionEvent)) {
                Log.e("Debug", "hit pose = "+hit.hitPose.toString() + "\n object Viking= " + vikingAttachment?.pose.toString() + "\n object Canon= " + cannonAttachment?.pose.toString() + "\n object pose= " + targetAttachment?.pose.toString())
                // Check if any plane was hit, and if it was hit inside the plane polygon.
                if (hit is PlaneHitResult && hit.isHitInPolygon) {
                    when (mode) {
                        Mode.VIKING -> vikingAttachment = addSessionAnchorFromAttachment(vikingAttachment, hit)
                        Mode.CANNON -> cannonAttachment = addSessionAnchorFromAttachment(cannonAttachment, hit)
                        Mode.TARGET -> targetAttachment = addSessionAnchorFromAttachment(targetAttachment, hit)
                    }
                    // Hits are sorted by depth. Consider only closest hit on a plane.
                    break
                }
            }
        }
    }

    private fun visualizeTrackedPoints(frame: Frame, projectionMatrix: FloatArray, viewMatrix: FloatArray) {
        pointCloud.update(frame.pointCloud)
        pointCloud.draw(frame.pointCloudPose, viewMatrix, projectionMatrix)
    }

    private fun checkPlaneDetected() {
        // Check if we detected at least one plane. If so, hide the loading message.
        for (plane in session.allPlanes) {
            if (plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING && plane.trackingState == Plane.TrackingState.TRACKING) {
                callBack.onPlanesDetected()
                break
            }
        }
    }

    private fun visualizePlanes(frame: Frame, projectionMatrix: FloatArray) {
        planeRenderer.drawPlanes(session.allPlanes, frame.pose, projectionMatrix)
    }

    private fun drawObject(objectRenderer: ObjectRenderer, planeAttachment: PlaneAttachment?,
                           scaleFactor: Float, projectionMatrix: FloatArray, viewMatrix: FloatArray,
                           lightIntensity: Float) {
        if (planeAttachment?.isTracking == true) {

            planeAttachment.pose.toMatrix(anchorMatrix, 0)

            // Update and draw the model
            objectRenderer.updateModelMatrix(anchorMatrix, scaleFactor)
            objectRenderer.draw(viewMatrix, projectionMatrix, lightIntensity)
        }
    }

    private fun checkTrackingState(frame: Frame): Boolean {
        if (frame.trackingState == Frame.TrackingState.NOT_TRACKING) {
            return false
        }
        return true
    }

    private fun addSessionAnchorFromAttachment(
            previousAttachment: PlaneAttachment?, hit: PlaneHitResult): PlaneAttachment {

        previousAttachment?.let {
            session.removeAnchors(Arrays.asList(previousAttachment.anchor))
        }
        return PlaneAttachment(hit.plane, session.addAnchor(hit.hitPose))
    }
}