package com.example.emailextractor

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private val emails = ArrayList<Pair<String, String>>() // email, source url
    private val seen = HashSet<String>()
    private val display = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>

    private lateinit var input: EditText
    private lateinit var maxPagesInput: EditText
    private lateinit var followBox: CheckBox
    private lateinit var academicBox: CheckBox
    private lateinit var startBtn: Button
    private lateinit var status: TextView

    private var job: ExtractionJob? = null
    private val exportCode = 42

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(24), dp(16), dp(12))
        }

        root.addView(TextView(this).apply {
            text = "Email Extractor"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        input = EditText(this).apply {
            hint = "Keyword (e.g. btech professors CSE) or a page URL"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
        }
        root.addView(input, matchWidth())

        maxPagesInput = EditText(this).apply {
            hint = "Max pages to scan"
            setText("20")
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(maxPagesInput, matchWidth())

        followBox = CheckBox(this).apply {
            text = "Also open faculty / contact / staff pages on the same site"
            isChecked = true
        }
        root.addView(followBox)

        academicBox = CheckBox(this).apply {
            text = "Only academic addresses (.edu, .ac.in, .edu.in ...)"
        }
        root.addView(academicBox)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        startBtn = Button(this).apply { text = "Start" }
        val copyBtn = Button(this).apply { text = "Copy all" }
        val csvBtn = Button(this).apply { text = "Export CSV" }
        val w = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        row.addView(startBtn, w)
        row.addView(copyBtn, w)
        row.addView(csvBtn, w)
        root.addView(row, matchWidth())

        status = TextView(this).apply {
            text = "Enter a keyword or URL and tap Start."
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, display)
        val list = ListView(this).apply { this.adapter = this@MainActivity.adapter }
        root.addView(list, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        // A page shared from the browser pre-fills the box
        if (intent?.action == Intent.ACTION_SEND) {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { input.setText(it.trim()) }
        }

        startBtn.setOnClickListener {
            if (job != null) {
                job?.cancel()
                status.text = "Stopping…"
            } else {
                startJob()
            }
        }
        copyBtn.setOnClickListener { copyAll() }
        csvBtn.setOnClickListener { exportCsv() }
        list.setOnItemClickListener { _, _, pos, _ ->
            copyToClipboard(emails[pos].first)
            Toast.makeText(this, "Copied ${emails[pos].first}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
    )

    private fun startJob() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) {
            Toast.makeText(this, "Enter a keyword or URL", Toast.LENGTH_SHORT).show()
            return
        }
        val max = (maxPagesInput.text.toString().toIntOrNull() ?: 20).coerceIn(1, 100)

        emails.clear(); seen.clear(); display.clear(); adapter.notifyDataSetChanged()
        startBtn.text = "Stop"

        val newJob = ExtractionJob(
            input = text,
            maxPages = max,
            followLinks = followBox.isChecked,
            academicOnly = academicBox.isChecked,
            onEmail = { email, source ->
                runOnUiThread {
                    if (seen.add(email)) {
                        emails.add(email to source)
                        display.add("$email\n${Search.hostOf(source)}")
                        adapter.notifyDataSetChanged()
                    }
                }
            },
            onStatus = { msg -> runOnUiThread { status.text = "$msg  |  ${emails.size} email(s)" } },
            onDone = { pages ->
                runOnUiThread {
                    job = null
                    startBtn.text = "Start"
                    status.text = "Done. Scanned $pages page(s), found ${emails.size} unique email(s)."
                }
            }
        )
        job = newJob
        Thread { newJob.run() }.start()
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("emails", text))
    }

    private fun copyAll() {
        if (emails.isEmpty()) {
            Toast.makeText(this, "Nothing to copy yet", Toast.LENGTH_SHORT).show()
            return
        }
        copyToClipboard(emails.joinToString("\n") { it.first })
        Toast.makeText(this, "Copied ${emails.size} emails", Toast.LENGTH_SHORT).show()
    }

    private fun exportCsv() {
        if (emails.isEmpty()) {
            Toast.makeText(this, "Nothing to export yet", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, "emails.csv")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, exportCode)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != exportCode || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                val sb = StringBuilder("email,source_page\n")
                emails.forEach { (e, s) -> sb.append(csv(e)).append(',').append(csv(s)).append('\n') }
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            Toast.makeText(this, "Saved ${emails.size} emails", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun csv(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
}
