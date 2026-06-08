package com.yourapp.instagrambot

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.material.card.MaterialCardView

class MainActivity : AppCompatActivity() {

    private lateinit var etSearch: EditText
    private lateinit var btnStart: Button
    private lateinit var btnStop: Button
    private lateinit var btnImportCsv: Button
    private lateinit var btnEnableService: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvCommentCount: TextView
    private lateinit var tvServiceStatus: TextView
    private lateinit var viewStatusIndicator: View

    private lateinit var commentManager: CommentManager

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getStringExtra("status") ?: ""
            val count = intent.getIntExtra("commentsPosted", 0)
            tvStatus.text = status
            tvStats.text = "Comments Posted: $count"
        }
    }

    private val csvPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            if (commentManager.importCsv(uri)) {
                updateCommentCount()
                Toast.makeText(this, "Comments imported successfully!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Failed to import CSV", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        commentManager = CommentManager(this)

        initViews()
        setupClickListeners()
        updateCommentCount()
    }

    private fun initViews() {
        etSearch = findViewById(R.id.etSearch)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnImportCsv = findViewById(R.id.btnImportCsv)
        btnEnableService = findViewById(R.id.btnEnableService)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvCommentCount = findViewById(R.id.tvCommentCount)
        tvServiceStatus = findViewById(R.id.tvServiceStatus)
        viewStatusIndicator = findViewById(R.id.viewStatusIndicator)
    }

    private fun setupClickListeners() {
        btnStart.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                showAccessibilityDialog()
                return@setOnClickListener
            }
            
            val query = etSearch.text.toString().trim()
            InstagramBotService.getInstance()?.startBot(if (query.isEmpty()) null else query)
            tvStatus.text = "Bot starting..."
        }

        btnStop.setOnClickListener {
            InstagramBotService.getInstance()?.stopBot()
            tvStatus.text = "Bot stopping..."
        }

        btnImportCsv.setOnClickListener {
            csvPicker.launch("text/*")
        }

        btnEnableService.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    private fun updateCommentCount() {
        tvCommentCount.text = "${commentManager.getCommentCount()} comments loaded"
    }

    private fun checkServiceStatus() {
        if (isAccessibilityServiceEnabled()) {
            tvServiceStatus.text = "Accessibility Service: Active"
            viewStatusIndicator.setBackgroundResource(R.drawable.status_indicator_active)
            btnEnableService.visibility = View.GONE
        } else {
            tvServiceStatus.text = "Accessibility Service: Disabled"
            viewStatusIndicator.setBackgroundResource(R.drawable.status_indicator_inactive)
            btnEnableService.visibility = View.VISIBLE
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (enabledService in enabledServices) {
            val enabledServiceInfo = enabledService.resolveInfo.serviceInfo
            if (enabledServiceInfo.packageName == packageName && enabledServiceInfo.name == InstagramBotService::class.java.name) {
                return true
            }
        }
        return false
    }

    private fun showAccessibilityDialog() {
        Toast.makeText(this, "Please enable Instagram Bot in Accessibility Settings", Toast.LENGTH_LONG).show()
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        checkServiceStatus()
        LocalBroadcastManager.getInstance(this).registerReceiver(statusReceiver, IntentFilter("BOT_STATUS"))
    }

    override fun onPause() {
        super.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusReceiver)
    }
}
