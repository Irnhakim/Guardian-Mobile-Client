package id.irnhakim.guardian.core.services

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

class GuardianOverlayManager(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var currentOverlayView: View? = null

    fun showMessage(type: String, messageText: String, passwordText: String) {
        // Dismiss any existing overlay first
        dismissOverlay()

        // Create the root layout that dim-shades the screen background
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.parseColor("#990F1117"))
        }

        // Layout parameters for a system alert overlay window
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        if (type == "BLOCK") {
            // Full-screen overlay blocking layout
            val blockContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(32.dpToPx(), 32.dpToPx(), 32.dpToPx(), 32.dpToPx())
                background = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(Color.parseColor("#0F1117"), Color.parseColor("#161B27"))
                )
            }

            // Warning icon
            val iconView = TextView(context).apply {
                text = "⚠️"
                textSize = 50f
                gravity = Gravity.CENTER
            }
            blockContainer.addView(iconView)

            blockContainer.addView(createSpacer(24))

            // Title
            val titleView = TextView(context).apply {
                text = "Layar Dikunci Orang Tua"
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            blockContainer.addView(titleView)

            blockContainer.addView(createSpacer(12))

            // Message text
            val msgView = TextView(context).apply {
                text = messageText
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 15f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.2f)
            }
            blockContainer.addView(msgView)

            blockContainer.addView(createSpacer(32))

            // PIN Password input field
            val passwordInput = EditText(context).apply {
                hint = "Masukkan Sandi Pembuka Kunci"
                setHintTextColor(Color.parseColor("#64748B"))
                setTextColor(Color.WHITE)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C2333"))
                    cornerRadius = 10f.dpToPx()
                    setStroke(2, Color.parseColor("#334155"))
                }
            }
            blockContainer.addView(passwordInput)

            blockContainer.addView(createSpacer(8))

            // Error Text View (hidden by default)
            val errorText = TextView(context).apply {
                text = "Sandi salah! Silakan coba lagi."
                setTextColor(Color.parseColor("#EF4444"))
                textSize = 12f
                visibility = View.GONE
                gravity = Gravity.CENTER
            }
            blockContainer.addView(errorText)

            blockContainer.addView(createSpacer(16))

            // Styled unlock button
            val unlockButton = Button(context).apply {
                text = "Buka Kunci Layar"
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EF4444"))
                    cornerRadius = 10f.dpToPx()
                }

                setOnClickListener {
                    val input = passwordInput.text.toString()
                    if (input == passwordText) {
                        dismissOverlay()
                    } else {
                        errorText.visibility = View.VISIBLE
                        passwordInput.background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1C2333"))
                            cornerRadius = 10f.dpToPx()
                            setStroke(2, Color.parseColor("#EF4444"))
                        }
                    }
                }
            }
            blockContainer.addView(unlockButton)

            root.addView(blockContainer, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ))
        } else {
            // Standard centered pop-up dialog
            val cardContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(24.dpToPx(), 24.dpToPx(), 24.dpToPx(), 24.dpToPx())
                
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#1C2333"))
                    cornerRadius = 20f.dpToPx()
                    setStroke(2, Color.parseColor("#334155"))
                }
            }

            // Message emoji
            val iconView = TextView(context).apply {
                text = "💬"
                textSize = 30f
                gravity = Gravity.CENTER
            }
            cardContainer.addView(iconView)

            cardContainer.addView(createSpacer(16))

            // Card Title
            val titleView = TextView(context).apply {
                text = "Pesan Orang Tua"
                setTextColor(Color.parseColor("#F1F5F9"))
                textSize = 18f
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            cardContainer.addView(titleView)

            cardContainer.addView(createSpacer(12))

            // Message Body
            val msgView = TextView(context).apply {
                text = messageText
                setTextColor(Color.parseColor("#94A3B8"))
                textSize = 14f
                gravity = Gravity.CENTER
                setLineSpacing(0f, 1.2f)
            }
            cardContainer.addView(msgView)

            cardContainer.addView(createSpacer(24))

            // OK dismiss button
            val okButton = Button(context).apply {
                text = "OK"
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#5C7CFA"))
                    cornerRadius = 10f.dpToPx()
                }

                setOnClickListener {
                    dismissOverlay()
                }
            }
            cardContainer.addView(okButton)

            val cardParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(24.dpToPx(), 0, 24.dpToPx(), 0)
            }
            root.addView(cardContainer, cardParams)
        }

        // Post rendering view adding to WindowManager
        Handler(Looper.getMainLooper()).post {
            try {
                windowManager.addView(root, params)
                currentOverlayView = root
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun dismissOverlay() {
        currentOverlayView?.let { view ->
            Handler(Looper.getMainLooper()).post {
                try {
                    windowManager.removeView(view)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            currentOverlayView = null
        }
    }

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
    private fun Float.dpToPx(): Float = this * context.resources.displayMetrics.density

    private fun createSpacer(dp: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp.dpToPx()
            )
        }
    }
}
