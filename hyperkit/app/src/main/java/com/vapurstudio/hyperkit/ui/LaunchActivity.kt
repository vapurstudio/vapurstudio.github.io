package com.vapurstudio.hyperkit.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.vapurstudio.hyperkit.HiddenItem
import com.vapurstudio.hyperkit.R

/** Target of pinned home-screen shortcuts: opens the hidden screen and closes itself. */
class LaunchActivity : Activity() {
    companion object { const val EXTRA_ID = "id" }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val item = HiddenItem.byId(intent.getStringExtra(EXTRA_ID))
        if (item == null || !item.launch(this)) Toast.makeText(this, R.string.hidden_failed, Toast.LENGTH_SHORT).show()
        finish()
    }
}
